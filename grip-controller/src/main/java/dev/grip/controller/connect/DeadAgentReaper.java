package dev.grip.controller.connect;

import dev.grip.controller.GripControllerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Drops any Agent connection that has sent nothing — not even a heartbeat —
 * within {@code grip.connect.agent-timeout}. Catches a peer that has gone away
 * without a TCP FIN faster than the socket would.
 */
@Component
public class DeadAgentReaper {

    private static final Logger log = LoggerFactory.getLogger(DeadAgentReaper.class);

    private final AgentRegistry registry;
    private final AgentConnectHandler handler;
    private final Duration timeout;

    public DeadAgentReaper(AgentRegistry registry, AgentConnectHandler handler, GripControllerProperties properties) {
        this.registry = registry;
        this.handler = handler;
        this.timeout = properties.connect().agentTimeout();
    }

    @Scheduled(fixedDelayString = "${grip.connect.reaper-interval:PT5S}")
    void reap() {
        Instant now = Instant.now();
        for (AgentConnection connection : registry.connections()) {
            if (Duration.between(connection.lastInbound(), now).compareTo(timeout) > 0) {
                log.warn("agent {} silent for over {} — closing", connection.agentId(), timeout);
                handler.closed(connection, "agent-timeout");
            }
        }
    }
}
