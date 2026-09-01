package dev.grip.controller.connect;

import dev.grip.protocol.wire.ControlFrame;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One Agent's connection to the Controller, server side. Created when the
 * WebSocket opens; lives until either side closes it.
 *
 * <p>Stage 1 carries only connection-lifecycle frames over the
 * {@link ControlFrame provisional line framing}.
 */
public final class AgentConnection {

    private static final Logger log = LoggerFactory.getLogger(AgentConnection.class);

    public enum State { NEW, REGISTERED, CLOSED }

    private final String remote;
    private final FrameSink sink;
    private final Object writeLock = new Object();

    private volatile String agentId;
    private volatile State state = State.NEW;
    private volatile Instant lastInbound = Instant.now();
    private final AtomicReference<String> closeReason = new AtomicReference<>();

    public AgentConnection(String remote, FrameSink sink) {
        this.remote = remote;
        this.sink = sink;
    }

    public String agentId() {
        return agentId;
    }

    public String remote() {
        return remote;
    }

    public State state() {
        return state;
    }

    public Instant lastInbound() {
        return lastInbound;
    }

    public String closeReason() {
        return closeReason.get();
    }

    void markRegistered(String agentId) {
        this.agentId = agentId;
        this.state = State.REGISTERED;
    }

    void touch() {
        this.lastInbound = Instant.now();
    }

    /** Test hook: pretend nothing has arrived for a long time. */
    void markStaleForTest() {
        this.lastInbound = Instant.EPOCH;
    }

    /** Serialised so heartbeat and lifecycle frames never interleave on the wire. */
    public void send(ControlFrame frame) {
        synchronized (writeLock) {
            if (state == State.CLOSED) {
                return;
            }
            try {
                sink.send(frame.encode().strip());
            } catch (RuntimeException e) {
                log.debug("send to {} failed: {}", describe(), e.toString());
                closeQuietly("send-failed");
            }
        }
    }

    public void reject(RegisterRejectReason reason) {
        send(ControlFrame.of("REGISTER_REJECTED", reason.name()));
        close("rejected:" + reason.name());
    }

    public void close(String reason) {
        if (state == State.CLOSED) {
            return;
        }
        closeReason.compareAndSet(null, reason);
        state = State.CLOSED;
        synchronized (writeLock) {
            try {
                sink.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        log.info("agent connection closed ({}), agentId={}, reason={}", remote, agentId, reason);
    }

    void closeQuietly(String reason) {
        closeReason.compareAndSet(null, reason);
        state = State.CLOSED;
    }

    private String describe() {
        return agentId != null ? agentId + "@" + remote : remote;
    }
}
