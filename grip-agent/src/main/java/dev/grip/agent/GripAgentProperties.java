package dev.grip.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Agent configuration, bound from {@code grip.*}.
 *
 * @param agentId       the name this Agent registers as; the external host
 *                      {@code <agentId>.<controller base domain>} routes here
 * @param controllerUrl the {@code https://} base URL of the Controller the
 *                      Agent dials out to. Plain {@code http} is rejected.
 * @param targetUrl     the single internal HTTP service this Agent exposes,
 *                      e.g. {@code http://localhost:8080}
 * @param reconnect     outbound reconnect behaviour
 */
@ConfigurationProperties(prefix = "grip")
public record GripAgentProperties(String agentId, URI controllerUrl, URI targetUrl, Reconnect reconnect) {

    public GripAgentProperties {
        if (controllerUrl != null && !"https".equalsIgnoreCase(controllerUrl.getScheme())) {
            throw new IllegalArgumentException("grip.controller-url must be https, got: " + controllerUrl);
        }
        if (reconnect == null) {
            reconnect = new Reconnect(null, null, null, null);
        }
    }

    /**
     * @param initialBackoff    first retry delay after a lost connection
     * @param maxBackoff        ceiling for the exponential backoff
     * @param heartbeatInterval how often to send a heartbeat on an idle connection
     * @param heartbeatTimeout  no inbound frame for this long ⇒ the connection
     *                          is considered dead and is dropped (triggering a
     *                          reconnect)
     */
    public record Reconnect(Duration initialBackoff, Duration maxBackoff,
            Duration heartbeatInterval, Duration heartbeatTimeout) {

        public Reconnect {
            if (initialBackoff == null) {
                initialBackoff = Duration.ofSeconds(1);
            }
            if (maxBackoff == null) {
                maxBackoff = Duration.ofSeconds(30);
            }
            if (heartbeatInterval == null) {
                heartbeatInterval = Duration.ofSeconds(15);
            }
            if (heartbeatTimeout == null) {
                heartbeatTimeout = Duration.ofSeconds(45);
            }
        }
    }
}
