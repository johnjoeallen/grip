package dev.grip.controller.connect;

import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Handler logic against an {@link AgentConnection} backed by a fake sink. */
class AgentConnectHandlerTest {

    private final AgentRegistry registry = new AgentRegistry();
    private final AgentConnectHandler handler = new AgentConnectHandler(registry);

    private static final class RecordingSink implements FrameSink {
        final List<Frame> sent = new ArrayList<>();
        boolean closed;

        @Override
        public void send(Frame frame) {
            sent.add(frame);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private RecordingSink sink;
    private AgentConnection conn;

    private void newConnection() {
        sink = new RecordingSink();
        conn = new AgentConnection("test:1", sink);
    }

    @Test
    void admitsAValidRegisterThenAnswersPing() {
        newConnection();

        boolean ok = handler.register(conn, new Frame.Register(0, "alpha"));

        assertThat(ok).isTrue();
        assertThat(sink.sent).containsExactly(new Frame.RegisterOk());
        assertThat(registry.get("alpha")).contains(conn);

        handler.frame(conn, new Frame.Ping(42));
        assertThat(sink.sent).containsExactly(new Frame.RegisterOk(), new Frame.Pong(42));
    }

    @Test
    void rejectsUnsupportedVersion() {
        newConnection();
        assertThat(handler.register(conn, new Frame.Register(99, "alpha"))).isFalse();
        assertThat(sink.sent).containsExactly(new Frame.RegisterRejected(RegisterRejectReason.UNSUPPORTED_VERSION));
        assertThat(sink.closed).isTrue();
    }

    @Test
    void rejectsANonRegisterFirstFrame() {
        newConnection();
        assertThat(handler.register(conn, new Frame.Ping(1))).isFalse();
        assertThat(sink.sent).containsExactly(new Frame.RegisterRejected(RegisterRejectReason.MALFORMED));
    }

    @Test
    void rejectsADuplicate() {
        newConnection();
        handler.register(conn, new Frame.Register(0, "alpha"));

        newConnection();
        assertThat(handler.register(conn, new Frame.Register(0, "alpha"))).isFalse();
        assertThat(sink.sent).containsExactly(new Frame.RegisterRejected(RegisterRejectReason.DUPLICATE_AGENT_ID));
    }

    @Test
    void disconnectClearsTheRegistry() {
        newConnection();
        handler.register(conn, new Frame.Register(0, "alpha"));

        handler.closed(conn, "ws-closed");

        assertThat(conn.state()).isEqualTo(AgentConnection.State.CLOSED);
        assertThat(registry.get("alpha")).isEmpty();
    }
}
