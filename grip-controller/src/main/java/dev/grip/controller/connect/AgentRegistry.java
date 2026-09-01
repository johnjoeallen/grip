package dev.grip.controller.connect;

import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tracks which Agent is on which connection. In-memory and per-process — there
 * is no shared state between Controller instances (out of scope; a single
 * Controller is assumed).
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    /** Provisional; made configurable in the host-routing stage. */
    static final Set<String> RESERVED = Set.of("www", "api", "controller", "health", "admin", "grip");
    static final Pattern VALID_ID = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private final ConcurrentHashMap<String, AgentConnection> byId = new ConcurrentHashMap<>();

    public sealed interface Result {
        record Registered() implements Result {}
        record Rejected(RegisterRejectReason reason) implements Result {}
    }

    /**
     * Attempts to bind {@code connection} to {@code agentId}. Rejects a
     * malformed or reserved id, and a duplicate while the incumbent is still
     * connected.
     */
    public Result register(String agentId, AgentConnection connection) {
        if (agentId == null || !VALID_ID.matcher(agentId).matches()) {
            return new Result.Rejected(RegisterRejectReason.MALFORMED);
        }
        if (RESERVED.contains(agentId)) {
            return new Result.Rejected(RegisterRejectReason.RESERVED_AGENT_ID);
        }
        AgentConnection prior = byId.putIfAbsent(agentId, connection);
        if (prior != null && prior.state() != AgentConnection.State.CLOSED) {
            return new Result.Rejected(RegisterRejectReason.DUPLICATE_AGENT_ID);
        }
        if (prior != null) {
            // incumbent was already closed — take its place
            byId.put(agentId, connection);
        }
        connection.markRegistered(agentId);
        log.info("agent registered: {} ({})", agentId, connection.remote());
        return new Result.Registered();
    }

    /** Removes the mapping only if {@code connection} is still the current holder. */
    public void unregister(AgentConnection connection) {
        String id = connection.agentId();
        if (id != null) {
            byId.remove(id, connection);
            log.info("agent unregistered: {}", id);
        }
    }

    public Optional<AgentConnection> get(String agentId) {
        return Optional.ofNullable(byId.get(agentId));
    }

    public Set<String> connectedIds() {
        return Set.copyOf(byId.keySet());
    }

    public Collection<AgentConnection> connections() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
