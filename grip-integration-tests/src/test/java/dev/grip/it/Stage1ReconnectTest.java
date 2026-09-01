package dev.grip.it;

import dev.grip.agent.GripAgentProperties;
import dev.grip.agent.connect.AgentConnection;
import dev.grip.agent.connect.AgentConnectionManager;
import dev.grip.agent.connect.ConnectionState;
import dev.grip.controller.GripControllerApplication;
import dev.grip.controller.connect.AgentRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stage 1b: {@link AgentConnectionManager} keeps the connection up across a
 * drop, gives up on a permanent rejection, and deregisters on shutdown.
 */
@SpringBootTest(
        classes = GripControllerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.ssl.enabled=true",
                "server.ssl.key-store=classpath:tls/controller-keystore.p12",
                "server.ssl.key-store-password=changeit",
                "server.ssl.key-store-type=PKCS12",
                "grip.connect.agent-timeout=3s",
                "grip.connect.reaper-interval=PT0.5S"
        })
class Stage1ReconnectTest {

    @LocalServerPort
    int port;

    @Autowired
    AgentRegistry registry;

    private AgentConnectionManager manager;

    private GripAgentProperties props(String agentId) {
        return new GripAgentProperties(
                agentId,
                URI.create("https://localhost:" + port),
                URI.create("http://localhost:1"),
                new GripAgentProperties.Reconnect(
                        Duration.ofMillis(200), Duration.ofSeconds(2),
                        Duration.ofSeconds(1), Duration.ofSeconds(10)));
    }

    @AfterEach
    void stop() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void reconnectsAfterTheConnectionDrops() {
        manager = new AgentConnectionManager(props("alpha"), true);
        manager.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> manager.state() == ConnectionState.REGISTERED);

        AgentConnection first = manager.connection();
        first.close("simulated-drop");

        await().atMost(Duration.ofSeconds(10)).until(() ->
                manager.connection() != first && manager.state() == ConnectionState.REGISTERED);
        await().atMost(Duration.ofSeconds(5)).until(() -> registry.get("alpha").isPresent());
    }

    @Test
    void reconnectsAfterTheControllerDropsItForSilence() {
        // agent heartbeat interval 1s, controller agent-timeout 3s + reaper 0.5s:
        // the heartbeat should keep it alive, so force a drop by killing the
        // current socket and confirm the loop brings it back.
        manager = new AgentConnectionManager(props("alpha"), true);
        manager.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> manager.state() == ConnectionState.REGISTERED);

        AgentConnection first = manager.connection();
        registry.get("alpha").orElseThrow().close("controller-side-drop");

        await().atMost(Duration.ofSeconds(10)).until(() ->
                manager.connection() != first && manager.state() == ConnectionState.REGISTERED);
    }

    @Test
    void givesUpOnAPermanentRejection() {
        manager = new AgentConnectionManager(props("api"), true); // reserved id
        manager.start();

        await().atMost(Duration.ofSeconds(10)).until(() -> manager.stoppedPermanently());
        assertThat(manager.state()).isNotEqualTo(ConnectionState.REGISTERED);
        assertThat(registry.get("api")).isEmpty();
    }

    @Test
    void gracefulShutdownDeregistersTheAgent() {
        manager = new AgentConnectionManager(props("alpha"), true);
        manager.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> manager.state() == ConnectionState.REGISTERED);

        manager.stop();

        await().atMost(Duration.ofSeconds(5)).until(() -> registry.get("alpha").isEmpty());
    }
}
