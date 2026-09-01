package dev.grip.controller.connect;

import dev.grip.protocol.GripProtocol;
import dev.grip.protocol.wire.ControlFrame;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transport-independent Agent-connection logic: admit or reject a REGISTER,
 * then service lifecycle frames. Unit-tested directly against an {@link
 * AgentConnection} backed by a fake {@link FrameSink}; the WebSocket wiring is
 * a thin adapter over this.
 */
@Component
public class AgentConnectHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectHandler.class);

    private final AgentRegistry registry;

    public AgentConnectHandler(AgentRegistry registry) {
        this.registry = registry;
    }

    /** Handles the first frame on a new connection. Returns true once registered. */
    public boolean register(AgentConnection connection, ControlFrame frame) {
        connection.touch();
        if (!frame.is("REGISTER") || frame.arg(0) == null || frame.arg(1) == null) {
            connection.reject(RegisterRejectReason.MALFORMED);
            return false;
        }
        if (!String.valueOf(GripProtocol.VERSION).equals(frame.arg(1))) {
            connection.reject(RegisterRejectReason.UNSUPPORTED_VERSION);
            return false;
        }
        AgentRegistry.Result result = registry.register(frame.arg(0), connection);
        if (result instanceof AgentRegistry.Result.Rejected rejected) {
            log.info("REGISTER rejected for '{}' from {}: {}",
                    frame.arg(0), connection.remote(), rejected.reason());
            connection.reject(rejected.reason());
            return false;
        }
        connection.send(ControlFrame.of("REGISTER_OK"));
        return true;
    }

    /** Handles a steady-state frame from a registered Agent. */
    public void frame(AgentConnection connection, ControlFrame frame) {
        connection.touch();
        if (frame.is("PING")) {
            connection.send(ControlFrame.of("PONG"));
        } else if (frame.is("BYE")) {
            connection.close("agent-bye");
        } else if (!frame.is("PONG")) {
            log.debug("ignoring frame '{}' from {}", frame.verb(), connection.agentId());
        }
    }

    public void closed(AgentConnection connection, String reason) {
        registry.unregister(connection);
        connection.close(reason);
    }
}
