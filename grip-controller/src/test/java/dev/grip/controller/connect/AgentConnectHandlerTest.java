package dev.grip.controller.connect;

import dev.grip.protocol.wire.ControlFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Handler logic against an {@link AgentConnection} backed by a fake sink. */
class AgentConnectHandlerTest {

    private final AgentRegistry registry = new AgentRegistry();
    private final AgentConnectHandler handler = new AgentConnectHandler(registry);

    private static final class RecordingSink implements FrameSink {
        final List<String> sent = new ArrayList<>();
        boolean closed;

        @Override
        public void send(String line) {
            sent.add(line);
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

        boolean ok = handler.register(conn, ControlFrame.of("REGISTER", "alpha", "0"));

        assertThat(ok).isTrue();
        assertThat(sink.sent).containsExactly("REGISTER_OK");
        assertThat(registry.get("alpha")).contains(conn);

        handler.frame(conn, ControlFrame.of("PING"));
        assertThat(sink.sent).containsExactly("REGISTER_OK", "PONG");
    }

    @Test
    void rejectsUnsupportedVersion() {
        newConnection();
        assertThat(handler.register(conn, ControlFrame.of("REGISTER", "alpha", "99"))).isFalse();
        assertThat(sink.sent).containsExactly("REGISTER_REJECTED UNSUPPORTED_VERSION");
        assertThat(sink.closed).isTrue();
    }

    @Test
    void rejectsMalformedRegister() {
        newConnection();
        assertThat(handler.register(conn, ControlFrame.of("HELLO"))).isFalse();
        assertThat(sink.sent).containsExactly("REGISTER_REJECTED MALFORMED");
    }

    @Test
    void rejectsADuplicate() {
        newConnection();
        handler.register(conn, ControlFrame.of("REGISTER", "alpha", "0"));

        newConnection();
        assertThat(handler.register(conn, ControlFrame.of("REGISTER", "alpha", "0"))).isFalse();
        assertThat(sink.sent).containsExactly("REGISTER_REJECTED DUPLICATE_AGENT_ID");
    }

    @Test
    void byeClosesAndDisconnectClearsTheRegistry() {
        newConnection();
        handler.register(conn, ControlFrame.of("REGISTER", "alpha", "0"));

        handler.frame(conn, ControlFrame.of("BYE"));
        assertThat(conn.state()).isEqualTo(AgentConnection.State.CLOSED);

        handler.closed(conn, "ws-closed");
        assertThat(registry.get("alpha")).isEmpty();
    }
}
