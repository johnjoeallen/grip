package dev.grip.controller.connect;

import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.FrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The WebSocket endpoint an Agent connects to
 * ({@link dev.grip.protocol.GripProtocol#AGENT_CONNECT_PATH}). Each binary
 * message is one {@link Frame}. A thin adapter: everything else lives in
 * {@link AgentConnectHandler} and {@link AgentConnection}.
 */
@Component
public class GripWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GripWebSocketHandler.class);
    private static final String CONNECTION = "grip.connection";

    private final AgentConnectHandler handler;

    public GripWebSocketHandler(AgentConnectHandler handler) {
        this.handler = handler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AgentConnection connection = new AgentConnection(remoteOf(session), new SessionSink(session));
        session.getAttributes().put(CONNECTION, connection);
        log.debug("agent websocket opened: {}", connection.remote());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        AgentConnection connection = connection(session);
        if (connection == null) {
            return;
        }
        Frame frame;
        try {
            ByteBuffer payload = message.getPayload();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            frame = FrameCodec.decode(bytes);
        } catch (RuntimeException e) {
            log.warn("protocol error from {}: {} — closing", connection.remote(), e.toString());
            handler.closed(connection, "protocol-error");
            return;
        }

        if (connection.state() == AgentConnection.State.NEW) {
            handler.register(connection, frame);
            return;
        }
        if (frame.type().isChannelScoped()) {
            connection.deliverFrame(frame);
        } else {
            handler.frame(connection, frame);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        AgentConnection connection = connection(session);
        if (connection != null) {
            handler.closed(connection, "ws-closed:" + status.getCode());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        AgentConnection connection = connection(session);
        if (connection != null) {
            handler.closed(connection, "transport-error:" + exception.getClass().getSimpleName());
        }
    }

    private static AgentConnection connection(WebSocketSession session) {
        return (AgentConnection) session.getAttributes().get(CONNECTION);
    }

    private static String remoteOf(WebSocketSession session) {
        return session.getRemoteAddress() != null ? session.getRemoteAddress().toString() : session.getId();
    }

    /** Bridges {@link FrameSink} to a {@link WebSocketSession}. */
    private static final class SessionSink implements FrameSink {
        private final WebSocketSession session;

        private SessionSink(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void send(Frame frame) {
            try {
                session.sendMessage(new BinaryMessage(FrameCodec.encode(frame)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
