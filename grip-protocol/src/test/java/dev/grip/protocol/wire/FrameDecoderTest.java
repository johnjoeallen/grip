package dev.grip.protocol.wire;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrameDecoderTest {

    private final FrameDecoder decoder = new FrameDecoder();

    private List<Frame> drain() {
        List<Frame> out = new ArrayList<>();
        decoder.poll().ifPresent(f -> {
            out.add(f);
            out.addAll(drain());
        });
        return out;
    }

    @Test
    void emitsNothingUntilAWholeFrameHasArrived() {
        byte[] frame = FrameCodec.encode(new Frame.RequestData(1, "abcdef".getBytes(StandardCharsets.UTF_8)));

        for (int i = 0; i < frame.length - 1; i++) {
            decoder.offer(new byte[] {frame[i]});
            assertThat(decoder.poll()).isEmpty();
        }
        decoder.offer(new byte[] {frame[frame.length - 1]});
        assertThat(decoder.poll()).contains(new Frame.RequestData(1, "abcdef".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void splitsMultipleFramesDeliveredInOneChunk() {
        Frame a = new Frame.RequestStart(1, "GET", "/a", Headers.of());
        Frame b = new Frame.RequestEnd(1);
        Frame c = new Frame.Ping(9);

        byte[] chunk = concat(FrameCodec.encode(a), FrameCodec.encode(b), FrameCodec.encode(c));
        decoder.offer(chunk);

        assertThat(drain()).containsExactly(a, b, c);
        assertThat(decoder.buffered()).isZero();
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }
}
