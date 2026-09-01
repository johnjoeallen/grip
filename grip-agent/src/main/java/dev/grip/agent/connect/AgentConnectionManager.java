package dev.grip.agent.connect;

import dev.grip.agent.GripAgentProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Owns the Agent's single {@link AgentConnection}: builds the HTTP client with
 * default (validating) TLS, connects once the application is up, and closes it
 * on shutdown.
 *
 * <p>Stage 1: a single connect attempt. Reconnect with backoff is layered on
 * by a later issue via {@link AgentConnection#onClosed}.
 */
@Component
public class AgentConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectionManager.class);

    private final GripAgentProperties properties;
    private final boolean autoConnect;
    private final HttpClient http;
    private volatile AgentConnection connection;

    public AgentConnectionManager(GripAgentProperties properties,
            @Value("${grip.auto-connect:true}") boolean autoConnect) {
        this.properties = properties;
        this.autoConnect = autoConnect;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // No .sslContext(...): the JDK default is used, which does full
                // chain and hostname validation. There is no insecure mode.
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!autoConnect) {
            log.info("grip.auto-connect=false — not opening a Controller connection");
            return;
        }
        connection = new AgentConnection(http, properties.controllerUrl(), properties.agentId(), Duration.ofSeconds(20));
        Thread.ofVirtual().name("grip-agent-connect").start(() -> {
            try {
                connection.connect();
            } catch (Exception e) {
                log.error("Agent could not connect to Controller at {}: {}",
                        properties.controllerUrl(), e.getMessage());
            }
        });
    }

    @PreDestroy
    public void stop() {
        AgentConnection c = connection;
        if (c != null) {
            c.send(dev.grip.protocol.wire.ControlFrame.of("BYE"));
            c.close("agent-shutdown");
        }
    }

    public ConnectionState state() {
        AgentConnection c = connection;
        return c == null ? ConnectionState.IDLE : c.state();
    }

    /** Package-visible for tests that need to await registration. */
    public AgentConnection connection() {
        return connection;
    }
}
