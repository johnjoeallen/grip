package dev.grip.it;

import dev.grip.agent.GripAgentProperties;
import dev.grip.agent.connect.AgentConnectionManager;
import dev.grip.agent.connect.ConnectionState;
import dev.grip.controller.GripControllerApplication;
import dev.grip.controller.connect.AgentRegistry;
import dev.grip.protocol.wire.CancelReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stage 3.5: {@code CANCEL} and {@code ERROR} flow end to end over the binary
 * frame protocol.
 */
@SpringBootTest(
        classes = GripControllerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.ssl.enabled=true",
                "server.ssl.key-store=classpath:tls/controller-keystore.p12",
                "server.ssl.key-store-password=changeit",
                "server.ssl.key-store-type=PKCS12",
                "grip.base-domain=grip.test"
        })
class Stage3FramingTest {

    @LocalServerPort
    int port;

    @Autowired
    AgentRegistry registry;

    private TrivialHttpService internal;
    private AgentConnectionManager agent;
    private final HttpClient external = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        internal = TrivialHttpService.start();
        agent = new AgentConnectionManager(new GripAgentProperties(
                "alpha", URI.create("https://localhost:" + port), URI.create(internal.baseUrl()),
                new GripAgentProperties.Reconnect(Duration.ofMillis(200), Duration.ofSeconds(2),
                        Duration.ofSeconds(2), Duration.ofSeconds(20))), true);
        agent.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> agent.state() == ConnectionState.REGISTERED);
    }

    @AfterEach
    void tearDown() {
        agent.stop();
        internal.close();
    }

    private CompletableFuture<HttpResponse<String>> send(String path) {
        return external.sendAsync(HttpRequest.newBuilder(URI.create("https://localhost:" + port + path))
                .header("Host", "alpha.grip.test").GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anInternalFailureBecomesAnErrorFrameAndA502() throws Exception {
        internal.close();

        HttpResponse<String> response = send("/status").get(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(response.statusCode()).isEqualTo(502);
    }

    @Test
    void aControllerCancelUnblocksTheRequestAndAbortsTheInternalCall() throws Exception {
        CompletableFuture<HttpResponse<String>> inflight = send("/slow?ms=4000");
        // wait until the Agent has actually started the internal call
        await().atMost(Duration.ofSeconds(3)).until(() -> internal.activeRequests() >= 1);

        registry.get("alpha").orElseThrow().cancelInFlight(CancelReason.CLIENT_GONE);

        HttpResponse<String> response = inflight.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).contains("CLIENT_GONE");

        // the Agent aborted the internal request rather than waiting the full 4s
        await().atMost(Duration.ofSeconds(3)).until(() -> internal.activeRequests() == 0);
    }
}
