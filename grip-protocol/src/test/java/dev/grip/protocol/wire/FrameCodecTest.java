package dev.grip.protocol.wire;

import dev.grip.protocol.GripProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class FrameCodecTest {

    private static final Headers HEADERS = new Headers()
            .add("Accept", "*/*")
            .add("X-Multi", "a")
            .add("X-Multi", "b");

    private static List<Frame> samples() {
        return List.of(
                new Frame.Register(0, "alpha-1"),
                new Frame.RegisterOk(),
                new Frame.RegisterRejected(RegisterRejectReason.DUPLICATE_AGENT_ID),
                new Frame.RequestStart(1001, "POST", "/books?author=x", HEADERS),
                new Frame.RequestData(1001, "hello world".getBytes(StandardCharsets.UTF_8)),
                new Frame.RequestEnd(1001),
                new Frame.ResponseStart(1001, 204, new Headers().add("X-A", "1")),
                new Frame.ResponseData(1001, new byte[] {0, 1, 2, -1, -2}),
                new Frame.ResponseEnd(1001),
                new Frame.Cancel(1001, CancelReason.CLIENT_GONE),
                new Frame.Error(1001, ErrorCode.BAD_GATEWAY, "connection refused"),
                new Frame.Ping(0xdeadbeefL),
                new Frame.Pong(0xdeadbeefL));
    }

    @TestFactory
    List<org.junit.jupiter.api.DynamicTest> everyFrameKindRoundTrips() {
        return samples().stream()
                .map(frame -> dynamicTest(frame.type().name(),
                        () -> assertThat(FrameCodec.decode(FrameCodec.encode(frame))).isEqualTo(frame)))
                .toList();
    }

    @Test
    void headerOrderAndRepeatsSurvive() {
        Frame.RequestStart in = new Frame.RequestStart(7, "GET", "/", HEADERS);
        Frame.RequestStart out = (Frame.RequestStart) FrameCodec.decode(FrameCodec.encode(in));
        assertThat(out.headers().fields()).containsExactly(
                new Headers.Field("Accept", "*/*"),
                new Headers.Field("X-Multi", "a"),
                new Headers.Field("X-Multi", "b"));
    }

    @Test
    void rejectsUnknownTypeAndNonZeroFlags() {
        byte[] good = FrameCodec.encode(new Frame.Ping(1));
        byte[] badType = good.clone();
        badType[0] = 0x7e;
        assertThatThrownBy(() -> FrameCodec.decode(badType)).isInstanceOf(ProtocolException.class);

        byte[] badFlags = good.clone();
        badFlags[1] = 1;
        assertThatThrownBy(() -> FrameCodec.decode(badFlags)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void rejectsAChannelScopeMismatch() {
        byte[] ping = FrameCodec.encode(new Frame.Ping(1));
        ping[9] = 5; // set a non-zero channel on a connection-scoped frame
        assertThatThrownBy(() -> FrameCodec.decode(ping)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void rejectsAnOversizedDataFrame() {
        assertThatThrownBy(() -> FrameCodec.encode(
                new Frame.RequestData(1, new byte[GripProtocol.MAX_FRAME_PAYLOAD_BYTES + 1])))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void decodeThrowsUnderflowOnAnIncompleteFrame() {
        byte[] full = FrameCodec.encode(new Frame.RequestData(1, new byte[100]));
        byte[] partial = new byte[full.length - 10];
        System.arraycopy(full, 0, partial, 0, partial.length);
        assertThatThrownBy(() -> FrameCodec.decode(partial))
                .isInstanceOf(java.nio.BufferUnderflowException.class);
    }
}
