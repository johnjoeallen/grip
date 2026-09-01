package dev.grip.controller.connect;

import dev.grip.protocol.GripProtocol;
import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transport-independent Agent-connection logic: admit or reject a REGISTER,
 * then service lifecycle frames. Unit-tested directly against an {@link
 * AgentConnection} backed by a fake {@link FrameSink}.
 */
@Component
public class AgentConnectHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectHandler.class);

    private final AgentRegistry registry;

    public AgentConnectHandler(AgentRegistry registry) {
        this.registry = registry;
    }

    /** Handles the first frame on a new connection. Returns true once registered. */
    public boolean register(AgentConnection connection, Frame frame) {
        connection.touch();
        if (!(frame instanceof Frame.Register register)) {
            connection.reject(RegisterRejectReason.MALFORMED);
            return false;
        }
        if (register.protocolVersion() != GripProtocol.VERSION) {
            connection.reject(RegisterRejectReason.UNSUPPORTED_VERSION);
            return false;
        }
        AgentRegistry.Result result = registry.register(register.agentId(), connection);
        if (result instanceof AgentRegistry.Result.Rejected rejected) {
            log.info("REGISTER rejected for '{}' from {}: {}",
                    register.agentId(), connection.remote(), rejected.reason());
            connection.reject(rejected.reason());
            return false;
        }
        connection.send(new Frame.RegisterOk());
        return true;
    }

    /** Handles a connection-scoped frame from a registered Agent. */
    public void frame(AgentConnection connection, Frame frame) {
        connection.touch();
        if (frame instanceof Frame.Ping ping) {
            connection.send(new Frame.Pong(ping.nonce()));
        } else if (!(frame instanceof Frame.Pong)) {
            log.debug("ignoring connection frame {} from {}", frame.type(), connection.agentId());
        }
    }

    public void closed(AgentConnection connection, String reason) {
        registry.unregister(connection);
        connection.close(reason);
    }
}
