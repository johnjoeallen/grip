package dev.grip.controller.connect;

import dev.grip.protocol.wire.ControlFrame;
import dev.grip.protocol.wire.ProxyCodec;
import dev.grip.protocol.wire.ProxyMessage;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
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

    // Stage 2: a single in-flight proxied request per connection.
    private final AtomicLong channelSeq = new AtomicLong(1000);
    private final AtomicReference<Exchange> exchange = new AtomicReference<>();

    /** One in-flight proxied request. */
    public record Exchange(long channel, CompletableFuture<ProxyMessage> reply) { }

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

    /** Claims the single Stage 2 channel, or empty if a request is already in flight. */
    public Optional<Exchange> beginExchange() {
        Exchange candidate = new Exchange(channelSeq.incrementAndGet(), new CompletableFuture<>());
        return exchange.compareAndSet(null, candidate) ? Optional.of(candidate) : Optional.empty();
    }

    public void endExchange(long channel) {
        exchange.updateAndGet(current -> current != null && current.channel() == channel ? null : current);
    }

    /** Routes a proxy reply from the Agent to the waiting request thread. */
    public void deliverProxy(ProxyMessage message) {
        Exchange current = exchange.get();
        if (current != null && current.channel() == message.channel()) {
            current.reply().complete(message);
        }
    }

    public void sendProxy(ProxyMessage message) {
        synchronized (writeLock) {
            if (state == State.CLOSED) {
                return;
            }
            try {
                sink.send(ProxyCodec.encode(message));
            } catch (RuntimeException e) {
                log.debug("proxy send to {} failed: {}", describe(), e.toString());
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
        Exchange pending = exchange.getAndSet(null);
        if (pending != null && !pending.reply().isDone()) {
            pending.reply().completeExceptionally(new IOException("agent connection closed: " + reason));
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
