package dev.grip.controller;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controller configuration, bound from {@code grip.*}.
 *
 * @param baseDomain       the domain under which Agents are exposed, e.g.
 *                         {@code grip.example.com}; an external host of
 *                         {@code alpha.grip.example.com} routes to Agent
 *                         {@code alpha}. Never hard-coded — must be configured.
 * @param connect          Agent-connection settings
 */
@ConfigurationProperties(prefix = "grip")
public record GripControllerProperties(String baseDomain, Connect connect) {

    public GripControllerProperties {
        if (connect == null) {
            connect = new Connect(null, null);
        }
    }

    /**
     * @param heartbeatInterval how often the Controller expects/sends a heartbeat
     * @param agentTimeout      how long without a heartbeat before an Agent
     *                          connection is considered dead
     */
    public record Connect(java.time.Duration heartbeatInterval, java.time.Duration agentTimeout) {

        public Connect {
            if (heartbeatInterval == null) {
                heartbeatInterval = java.time.Duration.ofSeconds(15);
            }
            if (agentTimeout == null) {
                agentTimeout = java.time.Duration.ofSeconds(45);
            }
        }
    }
}
