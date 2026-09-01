package dev.grip.agent.connect;

import dev.grip.agent.GripAgentProperties;
import dev.grip.protocol.wire.ControlFrame;
import dev.grip.protocol.wire.RegisterRejectReason;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the Agent's single {@link AgentConnection} and keeps it up: connect once
 * the application is ready, and after any drop reconnect with exponential
 * backoff. A REGISTER rejection that cannot resolve itself (bad id, bad
 * version, reserved name) stops the loop; a duplicate or a transient failure
 * does not.
 */
@Component
public class AgentConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectionManager.class);

    /** Rejections where retrying would only produce the same answer. */
    private static final Set<RegisterRejectReason> FATAL = Set.of(
            RegisterRejectReason.MALFORMED,
            RegisterRejectReason.UNSUPPORTED_VERSION,
            RegisterRejectReason.RESERVED_AGENT_ID);

    private final GripAgentProperties properties;
    private final boolean autoConnect;
    private final HttpClient http;

    private final AtomicReference<AgentConnection> current = new AtomicReference<>();
    private volatile Thread loop;
    private volatile boolean stopping;
    private volatile boolean stoppedPermanently;

    public AgentConnectionManager(GripAgentProperties properties,
            @Value("${grip.auto-connect:true}") boolean autoConnect) {
        this.properties = properties;
        this.autoConnect = autoConnect;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // No .sslContext(...): the JDK default is used — full chain and
                // hostname validation. There is no insecure mode.
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!autoConnect) {
            log.info("grip.auto-connect=false — not opening a Controller connection");
            return;
        }
        loop = Thread.ofVirtual().name("grip-agent-connect-loop").start(this::runLoop);
    }

    private void runLoop() {
        GripAgentProperties.Reconnect cfg = properties.reconnect();
        Duration backoff = cfg.initialBackoff();

        while (!stopping) {
            Instant attemptStart = Instant.now();
            AgentConnection connection = new AgentConnection(
                    http, properties.controllerUrl(), properties.agentId(),
                    Duration.ofSeconds(20), cfg.heartbeatInterval(), cfg.heartbeatTimeout());
            current.set(connection);

            try {
                connection.connect();
                log.info("connected to Controller");
                backoff = cfg.initialBackoff();
                connection.closed().get();               // block until this connection ends
            } catch (Exception e) {
                RegisterRejectReason reject = connection.rejectReason().orElse(null);
                if (reject != null && FATAL.contains(reject)) {
                    log.error("Controller rejected this Agent permanently ({}). Not retrying.", reject);
                    stoppedPermanently = true;
                    return;
                }
                log.warn("connection attempt failed: {}", messageOf(e));
            }

            if (stopping) {
                return;
            }
            // Reset the backoff if the last connection was stable for a while.
            if (Duration.between(attemptStart, Instant.now()).compareTo(cfg.initialBackoff().multipliedBy(4)) > 0) {
                backoff = cfg.initialBackoff();
            }
            sleep(withJitter(backoff));
            backoff = min(backoff.multipliedBy(2), cfg.maxBackoff());
        }
    }

    @PreDestroy
    public void stop() {
        stopping = true;
        AgentConnection c = current.get();
        if (c != null) {
            c.send(ControlFrame.of("BYE"));
            c.close("agent-shutdown");
        }
        Thread l = loop;
        if (l != null) {
            l.interrupt();
            try {
                l.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public ConnectionState state() {
        AgentConnection c = current.get();
        return c == null ? ConnectionState.IDLE : c.state();
    }

    /** True once the connect loop has given up (a permanent REGISTER rejection). */
    public boolean stoppedPermanently() {
        return stoppedPermanently;
    }

    public AgentConnection connection() {
        return current.get();
    }

    private void sleep(Duration d) {
        try {
            TimeUnit.MILLISECONDS.sleep(Math.max(0, d.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopping = true;
        }
    }

    private static Duration withJitter(Duration d) {
        long ms = d.toMillis();
        long jitter = ThreadLocalRandom.current().nextLong(ms / 4 + 1);
        return Duration.ofMillis(ms - ms / 8 + jitter);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String messageOf(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
