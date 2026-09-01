package dev.grip.controller.connect;

import dev.grip.protocol.GripProtocol;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final GripWebSocketHandler handler;

    WebSocketConfig(GripWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // No setAllowedOrigins: this endpoint is for Agents, not browsers, and
        // there is no cookie/session auth to protect from CSWSH. Agent
        // authentication is a future enhancement (see docs/security.md).
        registry.addHandler(handler, GripProtocol.AGENT_CONNECT_PATH).setAllowedOriginPatterns("*");
    }
}
