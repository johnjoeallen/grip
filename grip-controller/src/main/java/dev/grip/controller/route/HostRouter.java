package dev.grip.controller.route;

import dev.grip.controller.GripControllerProperties;
import dev.grip.controller.connect.AgentConnection;
import dev.grip.controller.connect.AgentRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Maps an external request's {@code Host} to a connected Agent.
 *
 * <p>Routing is by sub-domain: {@code <agentId>.<base-domain>} → Agent
 * {@code agentId}. The base domain comes from {@code grip.base-domain} and is
 * never hard-coded. Stage 2 accepts exactly one label before the base domain;
 * richer rules (reserved names, multi-label) are Stage 6.
 */
@Component
public class HostRouter {

    private final AgentRegistry registry;
    private final String baseDomain;

    public HostRouter(AgentRegistry registry, GripControllerProperties properties) {
        this.registry = registry;
        this.baseDomain = normalise(properties.baseDomain());
    }

    public sealed interface Route {
        /** The host is under the base domain and the Agent is connected. */
        record Resolved(String agentId, AgentConnection connection) implements Route {}

        /** The host is not {@code <label>.<base-domain>}. */
        record NotAnAgentHost(String host) implements Route {}

        /** Well-formed agent host, but no Agent by that id has ever connected here. */
        record UnknownAgent(String agentId) implements Route {}

        /** Known agent id, but its connection is not currently up. */
        record AgentDisconnected(String agentId) implements Route {}
    }

    /** True if this host should be handled as a proxy request rather than passed to the Controller's own endpoints. */
    public boolean isAgentHost(String hostHeader) {
        return agentIdOf(hostHeader) != null;
    }

    public Route resolve(String hostHeader) {
        String agentId = agentIdOf(hostHeader);
        if (agentId == null) {
            return new Route.NotAnAgentHost(String.valueOf(hostHeader));
        }
        return registry.get(agentId)
                .<Route>map(c -> c.state() == AgentConnection.State.REGISTERED
                        ? new Route.Resolved(agentId, c)
                        : new Route.AgentDisconnected(agentId))
                .orElseGet(() -> new Route.UnknownAgent(agentId));
    }

    /** The single label before {@code .<base-domain>}, or null if the host is not an agent host. */
    String agentIdOf(String hostHeader) {
        if (hostHeader == null || baseDomain.isEmpty()) {
            return null;
        }
        String host = normalise(stripPort(hostHeader));
        String suffix = "." + baseDomain;
        if (!host.endsWith(suffix)) {
            return null;
        }
        String label = host.substring(0, host.length() - suffix.length());
        if (label.isEmpty() || label.contains(".")) {
            return null;
        }
        return label;
    }

    private static String stripPort(String host) {
        int colon = host.lastIndexOf(':');
        // ignore a colon inside a bracketed IPv6 literal
        return colon > host.lastIndexOf(']') ? host.substring(0, colon) : host;
    }

    private static String normalise(String host) {
        if (host == null) {
            return "";
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
    }
}
