package dev.grip.it;

import dev.grip.agent.connect.AgentConnection;
import dev.grip.agent.connect.ConnectionConfig;
import dev.grip.agent.connect.ConnectionState;
import dev.grip.controller.GripControllerApplication;
import dev.grip.controller.connect.AgentRegistry;
import dev.grip.protocol.wire.Frame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Stage 1 end to end: a real Agent {@link AgentConnection} dials a real
 * {@link GripControllerApplication} over TLS + HTTP/2, registers, exchanges
 * heartbeats, and is cleaned up on disconnect.
 */
@SpringBootTest(
        classes = GripControllerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.ssl.enabled=true",
                "server.ssl.key-store=classpath:tls/controller-keystore.p12",
                "server.ssl.key-store-password=changeit",
                "server.ssl.key-store-type=PKCS12"
        })
class Stage1ConnectTest {

    @LocalServerPort
    int port;

    @Autowired
    AgentRegistry registry;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final List<AgentConnection> opened = new ArrayList<>();

    private AgentConnection agent(String agentId) {
        AgentConnection c = new AgentConnection(client, new ConnectionConfig(
                URI.create("https://localhost:" + port), URI.create("http://localhost:1"), agentId,
                Duration.ofSeconds(15), Duration.ofSeconds(1), Duration.ofSeconds(20)));
        opened.add(c);
        return c;
    }

    @AfterEach
    void closeAll() {
        opened.forEach(c -> c.close("test-teardown"));
    }

    @Test
    void agentConnectsOverTlsAndRegisters() throws Exception {
        AgentConnection alpha = agent("alpha");

        alpha.connect();
        alpha.awaitRegistered(Duration.ofSeconds(10));

        assertThat(alpha.state()).isEqualTo(ConnectionState.REGISTERED);
        await().atMost(Duration.ofSeconds(5)).until(() -> registry.get("alpha").isPresent());
    }

    @Test
    void heartbeatIsAnswered() throws Exception {
        AgentConnection alpha = agent("alpha");
        alpha.connect();
        alpha.awaitRegistered(Duration.ofSeconds(10));

        var before = alpha.lastInbound();
        alpha.send(new Frame.Ping(1));

        await().atMost(Duration.ofSeconds(5)).until(() -> alpha.lastInbound().isAfter(before));
    }

    @Test
    void theAutomaticHeartbeatKeepsTheConnectionAlive() throws Exception {
        AgentConnection alpha = agent("alpha");   // 1s heartbeat interval, 20s timeout
        alpha.connect();
        alpha.awaitRegistered(Duration.ofSeconds(10));

        // No manual traffic — the connection's own heartbeat must keep it up
        // and keep lastInbound fresh (Controller answers each PING with PONG).
        Thread.sleep(3_500);

        assertThat(alpha.state()).isEqualTo(ConnectionState.REGISTERED);
        assertThat(java.time.Duration.between(alpha.lastInbound(), java.time.Instant.now()))
                .isLessThan(Duration.ofSeconds(3));
        assertThat(registry.get("alpha")).isPresent();
    }

    @Test
    void aSecondAgentWithTheSameIdIsRejected() throws Exception {
        AgentConnection first = agent("alpha");
        first.connect();
        first.awaitRegistered(Duration.ofSeconds(10));

        AgentConnection dup = agent("alpha");
        assertThatThrownBy(() -> dup.connect()).hasMessageContaining("DUPLICATE_AGENT_ID");
        assertThat(dup.state()).isEqualTo(ConnectionState.DISCONNECTED);
    }

    @Test
    void aReservedIdIsRejected() {
        AgentConnection api = agent("api");
        assertThatThrownBy(() -> api.connect()).hasMessageContaining("RESERVED_AGENT_ID");
    }

    @Test
    void agentDisconnectClearsTheRegistry() throws Exception {
        AgentConnection alpha = agent("alpha");
        alpha.connect();
        alpha.awaitRegistered(Duration.ofSeconds(10));
        await().atMost(Duration.ofSeconds(5)).until(() -> registry.get("alpha").isPresent());

        alpha.close("client-gone");

        await().atMost(Duration.ofSeconds(10)).until(() -> registry.get("alpha").isEmpty());
    }
}
