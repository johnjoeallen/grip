package dev.grip.it;

import dev.grip.agent.GripAgentProperties;
import dev.grip.agent.connect.AgentConnectionManager;
import dev.grip.agent.connect.ConnectionState;
import dev.grip.controller.GripControllerApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stage 2 end to end: an ordinary HTTPS request to the Controller is proxied
 * through the Agent to a trivial internal service and back.
 *
 * <pre>
 *   HttpClient --https--> Controller --ws--> Agent --http--> TrivialHttpService
 * </pre>
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
class Stage2ProxyTest {

    @LocalServerPort
    int port;

    private TrivialHttpService internal;
    private AgentConnectionManager agent;
    private final HttpClient external = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        internal = TrivialHttpService.start();
        agent = new AgentConnectionManager(new GripAgentProperties(
                "alpha",
                URI.create("https://localhost:" + port),
                URI.create(internal.baseUrl()),
                new GripAgentProperties.Reconnect(
                        Duration.ofMillis(200), Duration.ofSeconds(2),
                        Duration.ofSeconds(2), Duration.ofSeconds(20))), true);
        agent.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> agent.state() == ConnectionState.REGISTERED);
    }

    @AfterEach
    void tearDown() {
        agent.stop();
        internal.close();
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return external.send(HttpRequest.newBuilder(URI.create("https://localhost:" + port + path))
                .header("Host", "alpha.grip.test").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void proxiesAGet() throws Exception {
        HttpResponse<byte[]> response = get("/status");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body())).isEqualTo("ok");
    }

    @Test
    void proxiesAPostBody() throws Exception {
        HttpResponse<byte[]> response = external.send(
                HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/echo"))
                        .header("Host", "alpha.grip.test")
                        .POST(HttpRequest.BodyPublishers.ofString("round trip"))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body())).isEqualTo("round trip");
    }

    @Test
    void passesThroughTheInternalServicesOwnStatus() throws Exception {
        assertThat(get("/no-such-path").statusCode()).isEqualTo(404);
    }

    @Test
    void returns502WhenTheInternalServiceIsDown() throws Exception {
        internal.close();

        HttpResponse<byte[]> response = get("/status");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
    }

    @Test
    void aSecondConcurrentRequestGets503WhileTheFirstIsInFlight() throws Exception {
        // /slow?ms=1500 holds the single channel; a second request must be refused.
        var slow = external.sendAsync(
                HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/slow?ms=1500"))
                        .header("Host", "alpha.grip.test").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Thread.sleep(300);

        HttpResponse<byte[]> second = get("/status");
        assertThat(second.statusCode()).isEqualTo(503);

        assertThat(slow.get().statusCode()).isEqualTo(200);
    }
}
