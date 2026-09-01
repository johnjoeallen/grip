package dev.grip.agent.connect;

import dev.grip.protocol.GripProtocol;
import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.FrameCodec;
import dev.grip.protocol.wire.ProtocolException;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Agent's single long-lived outbound connection to the Controller: a
 * WebSocket over TLS carrying {@link Frame} binary messages.
 *
 * <p>WebSocket rather than a raw HTTP stream because the JDK HTTP client cannot
 * surface a response while a request body is still open. TLS validation is the
 * JDK default — full chain and hostname checks, no override, no insecure mode.
 */
public final class AgentConnection {

    private static final Logger log = LoggerFactory.getLogger(AgentConnection.class);

    private final HttpClient http;
    private final URI connectUri;
    private final String agentId;
    private final Duration handshakeTimeout;
    private final Duration heartbeatInterval;
    private final Duration heartbeatTimeout;
    private final RequestForwarder forwarder;

    private final Object writeLock = new Object();
    private final ByteArrayOutputStream inbound = new ByteArrayOutputStream();
    private final Map<Long, ForwardingChannel> channels = new ConcurrentHashMap<>();

    private volatile WebSocket socket;
    private volatile ConnectionState state = ConnectionState.IDLE;
    private final AtomicReference<String> closeReason = new AtomicReference<>();
    private final AtomicReference<RegisterRejectReason> rejectReason = new AtomicReference<>();
    private volatile Instant lastInbound = Instant.EPOCH;
    private final CompletableFuture<Void> registered = new CompletableFuture<>();
    private final CompletableFuture<String> closed = new CompletableFuture<>();

    private ScheduledExecutorService heartbeat;
    private ScheduledFuture<?> heartbeatTask;

    /** Invoked once when the connection ends. Later issues use this for reconnect. */
    public volatile Runnable onClosed = () -> { };

    public AgentConnection(HttpClient http, ConnectionConfig config) {
        this.http = http;
        this.connectUri = toWebSocketUri(config.controllerBase());
        this.agentId = config.agentId();
        this.handshakeTimeout = config.handshakeTimeout();
        this.heartbeatInterval = config.heartbeatInterval();
        this.heartbeatTimeout = config.heartbeatTimeout();
        this.forwarder = new RequestForwarder(http, config.targetUri(), Duration.ofSeconds(30));
    }

    static URI toWebSocketUri(URI controllerBase) {
        String scheme = switch (controllerBase.getScheme().toLowerCase()) {
            case "https", "wss" -> "wss";
            case "http", "ws" -> "ws";
            default -> throw new IllegalArgumentException("controller-url must be http(s): " + controllerBase);
        };
        return URI.create(scheme + "://" + controllerBase.getAuthority()).resolve(GripProtocol.AGENT_CONNECT_PATH);
    }

    public ConnectionState state() {
        return state;
    }

    public String closeReason() {
        return closeReason.get();
    }

    public Optional<RegisterRejectReason> rejectReason() {
        return Optional.ofNullable(rejectReason.get());
    }

    public Instant lastInbound() {
        return lastInbound;
    }

    public CompletableFuture<String> closed() {
        return closed;
    }

    public void awaitRegistered(Duration timeout) throws Exception {
        try {
            registered.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("not registered within " + timeout + " (state=" + state + ")");
        }
    }

    /** Opens the WebSocket and registers. Returns once REGISTER_OK arrives, or throws. Does not retry. */
    public void connect() throws Exception {
        state = ConnectionState.CONNECTING;
        try {
            socket = http.newWebSocketBuilder()
                    .connectTimeout(handshakeTimeout)
                    .buildAsync(connectUri, new Listener())
                    .get(handshakeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail("handshake-failed: " + rootMessage(e));
            throw e;
        }

        send(new Frame.Register(GripProtocol.VERSION, agentId));

        try {
            registered.get(handshakeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            close("register-failed");
            throw new IOException(closeReason() != null ? closeReason() : "REGISTER failed", e);
        }
        startHeartbeat();
    }

    private void startHeartbeat() {
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "grip-agent-heartbeat");
            t.setDaemon(true);
            return t;
        });
        long millis = Math.max(1000, heartbeatInterval.toMillis());
        heartbeatTask = heartbeat.scheduleAtFixedRate(this::heartbeatTick, millis, millis, TimeUnit.MILLISECONDS);
    }

    private void heartbeatTick() {
        if (state != ConnectionState.REGISTERED) {
            return;
        }
        if (Duration.between(lastInbound, Instant.now()).compareTo(heartbeatTimeout) > 0) {
            log.warn("no frame from Controller for {} — dropping connection", heartbeatTimeout);
            close("heartbeat-timeout");
            return;
        }
        send(new Frame.Ping(ThreadLocalRandom.current().nextLong()));
    }

    /** Serialised; the JDK WebSocket forbids a second send before the first completes. */
    public void send(Frame frame) {
        synchronized (writeLock) {
            WebSocket ws = socket;
            if (ws == null || state == ConnectionState.DISCONNECTED) {
                return;
            }
            try {
                ws.sendBinary(ByteBuffer.wrap(FrameCodec.encode(frame)), true).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                close("send-failed: " + rootMessage(e));
            }
        }
    }

    private void onFrame(Frame frame) {
        lastInbound = Instant.now();

        if (!registered.isDone()) {
            handleRegisterReply(frame);
            return;
        }
        switch (frame) {
            case Frame.Ping ping -> send(new Frame.Pong(ping.nonce()));
            case Frame.Pong ignored -> { }
            case Frame.RequestStart start -> channel(start.channel()).onStart(start);
            case Frame.RequestData data -> channel(data.channel()).onData(data);
            case Frame.RequestEnd end -> {
                ForwardingChannel c = channels.get(end.channel());
                if (c != null) {
                    c.onEnd();
                }
            }
            case Frame.Cancel cancel -> {
                ForwardingChannel c = channels.remove(cancel.channel());
                if (c != null) {
                    c.cancel();
                }
            }
            default -> log.debug("ignoring frame {}", frame.type());
        }
    }

    private ForwardingChannel channel(long id) {
        return channels.computeIfAbsent(id, k ->
                new ForwardingChannel(id, forwarder, this::sendChannelFrame));
    }

    private void sendChannelFrame(Frame frame) {
        send(frame);
        if (frame instanceof Frame.ResponseEnd || frame instanceof Frame.Error) {
            channels.remove(frame.channel());
        }
    }

    private void handleRegisterReply(Frame frame) {
        switch (frame) {
            case Frame.RegisterOk ignored -> {
                state = ConnectionState.REGISTERED;
                registered.complete(null);
                log.info("registered with Controller as '{}'", agentId);
            }
            case Frame.RegisterRejected rejected -> {
                rejectReason.set(rejected.reason());
                fail("rejected:" + rejected.reason());
            }
            default -> fail("unexpected-reply:" + frame.type());
        }
    }

    public void close(String reason) {
        if (state == ConnectionState.DISCONNECTED) {
            return;
        }
        closeReason.compareAndSet(null, reason);
        state = ConnectionState.DISCONNECTED;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
        channels.values().forEach(ForwardingChannel::cancel);
        channels.clear();
        WebSocket ws = socket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(t -> null);
            ws.abort();
        }
        if (!registered.isDone()) {
            registered.completeExceptionally(new IOException("connection closed: " + reason));
        }
        closed.complete(reason);
        log.info("connection closed: {}", reason);
        try {
            onClosed.run();
        } catch (RuntimeException e) {
            log.warn("onClosed handler threw", e);
        }
    }

    private void fail(String reason) {
        closeReason.compareAndSet(null, reason);
        state = ConnectionState.DISCONNECTED;
        if (!registered.isDone()) {
            registered.completeExceptionally(new IOException(reason));
        }
        closed.complete(reason);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + (cur.getMessage() != null ? ": " + cur.getMessage() : "");
    }

    /** Reassembles fragmented binary messages, one frame per message. */
    private final class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            inbound.writeBytes(chunk);
            if (last) {
                byte[] message = inbound.toByteArray();
                inbound.reset();
                try {
                    onFrame(FrameCodec.decode(message));
                } catch (ProtocolException e) {
                    log.warn("protocol error from Controller: {}", e.getMessage());
                    close("protocol-error");
                } catch (RuntimeException e) {
                    log.warn("frame handling failed", e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            close("controller-close:" + statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            close("ws-error: " + rootMessage(error));
        }
    }
}
