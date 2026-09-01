package dev.grip.controller.connect;

import dev.grip.protocol.wire.CancelReason;
import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One Agent's connection to the Controller, server side. Created when the
 * WebSocket opens; lives until either side closes it. Frames are the
 * {@link Frame} binary format.
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

    // Stage 2/3: a single in-flight proxied request per connection (multiplexing is Stage 4).
    private final AtomicLong channelSeq = new AtomicLong(1000);
    private final AtomicReference<ProxyExchange> exchange = new AtomicReference<>();

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

    void markStaleForTest() {
        this.lastInbound = Instant.EPOCH;
    }

    /** Serialised so frames never interleave on the wire. */
    public void send(Frame frame) {
        synchronized (writeLock) {
            if (state == State.CLOSED) {
                return;
            }
            try {
                sink.send(frame);
            } catch (RuntimeException e) {
                log.debug("send to {} failed: {}", describe(), e.toString());
                closeQuietly("send-failed");
            }
        }
    }

    // ---- proxying ----

    /** Claims the single Stage 3 channel, or empty if a request is already in flight. */
    public Optional<ProxyExchange> beginExchange() {
        ProxyExchange candidate = new ProxyExchange(channelSeq.incrementAndGet());
        return exchange.compareAndSet(null, candidate) ? Optional.of(candidate) : Optional.empty();
    }

    public void endExchange(long channel) {
        exchange.updateAndGet(current -> current != null && current.channel() == channel ? null : current);
    }

    /** Routes a channel-scoped frame from the Agent to the waiting request. */
    public void deliverFrame(Frame frame) {
        ProxyExchange current = exchange.get();
        if (current != null && current.channel() == frame.channel()) {
            current.accept(frame);
        }
    }

    /** Tells the Agent to abandon the in-flight channel (client gone, shutdown, …). */
    public void cancelInFlight(CancelReason reason) {
        ProxyExchange current = exchange.get();
        if (current != null) {
            send(new Frame.Cancel(current.channel(), reason));
            current.cancelledLocally(reason);
        }
    }

    // ---- lifecycle ----

    public void reject(RegisterRejectReason reason) {
        send(new Frame.RegisterRejected(reason));
        close("rejected:" + reason.name());
    }

    public void close(String reason) {
        if (state == State.CLOSED) {
            return;
        }
        closeReason.compareAndSet(null, reason);
        state = State.CLOSED;
        ProxyExchange pending = exchange.getAndSet(null);
        if (pending != null) {
            pending.failClosed(reason);
        }
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
