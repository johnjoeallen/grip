package dev.grip.controller.connect;

import dev.grip.protocol.wire.ControlFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * The WebSocket endpoint an Agent connects to
 * ({@link dev.grip.protocol.GripProtocol#AGENT_CONNECT_PATH}). A thin adapter:
 * every concern beyond moving frames on and off the session lives in
 * {@link AgentConnectHandler}.
 */
@Component
public class GripWebSocketHandler extends TextWebSocketHandler {

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
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        AgentConnection connection = connection(session);
        if (connection == null) {
            return;
        }
        ControlFrame frame;
        try {
            frame = ControlFrame.parse(message.getPayload());
        } catch (RuntimeException e) {
            log.debug("bad frame from {}: {}", connection.remote(), message.getPayload());
            if (connection.state() == AgentConnection.State.NEW) {
                connection.reject(dev.grip.protocol.wire.RegisterRejectReason.MALFORMED);
            }
            return;
        }
        if (connection.state() == AgentConnection.State.NEW) {
            handler.register(connection, frame);
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
        public void send(String line) {
            try {
                session.sendMessage(new TextMessage(line));
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
