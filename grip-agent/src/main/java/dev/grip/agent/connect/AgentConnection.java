package dev.grip.agent.connect;

import dev.grip.protocol.GripProtocol;
import dev.grip.protocol.wire.ControlFrame;
import dev.grip.protocol.wire.ProxyCodec;
import dev.grip.protocol.wire.ProxyMessage;
import dev.grip.protocol.wire.RegisterRejectReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Agent's single long-lived outbound connection to the Controller: a
 * WebSocket over TLS.
 *
 * <p>WebSocket rather than a raw HTTP stream because the JDK HTTP client cannot
 * surface a response while a request body is still open, so a single
 * full-duplex HTTP request is not possible. The connection is still
 * Agent-initiated, one connection, plain {@code wss://} — see
 * {@code docs/protocol.md}.
 *
 * <p>TLS validation is the JDK default — full chain and hostname checks, no
 * override, no insecure mode.
 *
 * <p>Stage 1 scope: connect, REGISTER, hold the connection. Reconnect and
 * heartbeat are layered on via {@link #onClosed}.
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
    private final StringBuilder inbound = new StringBuilder();
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
        String base = scheme + "://" + controllerBase.getAuthority();
        return URI.create(base).resolve(GripProtocol.AGENT_CONNECT_PATH);
    }

    public ConnectionState state() {
        return state;
    }

    public String closeReason() {
        return closeReason.get();
    }

    /** Present when the connection ended because the Controller rejected REGISTER. */
    public java.util.Optional<RegisterRejectReason> rejectReason() {
        return java.util.Optional.ofNullable(rejectReason.get());
    }

    public Instant lastInbound() {
        return lastInbound;
    }

    /** Completes with the close reason when the connection ends. */
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

    /**
     * Opens the WebSocket and registers. Returns once REGISTER_OK arrives, or
     * throws if the attempt fails. Does not retry.
     */
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

        send(ControlFrame.of("REGISTER", agentId, String.valueOf(GripProtocol.VERSION)));

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
        send(ControlFrame.of("PING"));
    }

    /** Serialised; the JDK WebSocket forbids a second send before the first completes. */
    public void send(ControlFrame frame) {
        sendText(frame.encode().strip());
    }

    private void sendProxy(ProxyMessage message) {
        sendText(ProxyCodec.encode(message));
    }

    private void sendText(String text) {
        synchronized (writeLock) {
            WebSocket ws = socket;
            if (ws == null || state == ConnectionState.DISCONNECTED) {
                return;
            }
            try {
                ws.sendText(text, true).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                close("send-failed: " + rootMessage(e));
            }
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

    private void onMessage(String message) {
        lastInbound = Instant.now();

        if (ProxyCodec.isProxyMessage(message)) {
            handleProxyMessage(message);
            return;
        }

        ControlFrame frame;
        try {
            frame = ControlFrame.parse(message);
        } catch (RuntimeException e) {
            log.debug("ignoring unparseable frame: {}", message);
            return;
        }
        if (!registered.isDone()) {
            handleRegisterReply(frame);
            return;
        }
        if (frame.is("PING")) {
            send(ControlFrame.of("PONG"));
        } else if (frame.is("BYE")) {
            close("controller-bye");
        }
        // PONG and anything else: liveness only.
    }

    private void handleProxyMessage(String message) {
        ProxyMessage decoded;
        try {
            decoded = ProxyCodec.decode(message);
        } catch (RuntimeException e) {
            log.warn("undecodable proxy message dropped: {}", e.toString());
            return;
        }
        if (decoded instanceof ProxyMessage.Request request) {
            // Forward on a virtual thread so the WebSocket reader keeps flowing.
            Thread.ofVirtual().name("grip-agent-forward-" + request.channel()).start(() -> {
                ProxyMessage reply = forwarder.forward(request);
                sendProxy(reply);
            });
        }
        // Response/Failure are Controller->Agent only in this direction; ignore.
    }

    private void handleRegisterReply(ControlFrame frame) {
        if (frame.is("REGISTER_OK")) {
            state = ConnectionState.REGISTERED;
            registered.complete(null);
            log.info("registered with Controller as '{}'", agentId);
        } else if (frame.is("REGISTER_REJECTED")) {
            RegisterRejectReason reason = RegisterRejectReason.from(frame.arg(0));
            rejectReason.set(reason);
            fail("rejected:" + reason);
        } else {
            fail("unexpected-reply:" + frame.verb());
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + (cur.getMessage() != null ? ": " + cur.getMessage() : "");
    }

    /** Reassembles fragmented text messages into whole frame lines. */
    private final class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            inbound.append(data);
            if (last) {
                String message = inbound.toString();
                inbound.setLength(0);
                try {
                    onMessage(message);
                } catch (RuntimeException e) {
                    log.warn("message handling failed", e);
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
