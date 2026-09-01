package dev.grip.agent.connect;

import com.sun.net.httpserver.HttpServer;
import dev.grip.protocol.wire.ProxyMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestForwarderTest {

    private HttpServer server;
    private RequestForwarder forwarder;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", ex -> respond(ex, 200, "ok"));
        server.createContext("/echo", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            ex.getResponseHeaders().add("X-Seen-Ct", ex.getRequestHeaders().getFirst("Content-Type"));
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        forwarder = new RequestForwarder(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void forwardsAGetAndReturnsTheResponse() {
        ProxyMessage reply = forwarder.forward(new ProxyMessage.Request(
                5, "GET", "/status", Map.of(), new byte[0]));

        assertThat(reply).isInstanceOf(ProxyMessage.Response.class);
        ProxyMessage.Response r = (ProxyMessage.Response) reply;
        assertThat(r.channel()).isEqualTo(5);
        assertThat(r.status()).isEqualTo(200);
        assertThat(new String(r.body(), StandardCharsets.UTF_8)).isEqualTo("ok");
    }

    @Test
    void forwardsAPostBodyAndHeaders() {
        ProxyMessage.Response r = (ProxyMessage.Response) forwarder.forward(new ProxyMessage.Request(
                6, "POST", "/echo",
                Map.of("Content-Type", List.of("text/plain")),
                "round trip".getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(r.body(), StandardCharsets.UTF_8)).isEqualTo("round trip");
        assertThat(r.headers().entrySet())
                .anySatisfy(e -> {
                    assertThat(e.getKey()).isEqualToIgnoringCase("X-Seen-Ct");
                    assertThat(e.getValue()).containsExactly("text/plain");
                });
    }

    @Test
    void reportsFailureWhenTheInternalServiceIsUnreachable() {
        RequestForwarder dead = new RequestForwarder(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:1"), Duration.ofSeconds(2));

        ProxyMessage reply = dead.forward(new ProxyMessage.Request(9, "GET", "/", Map.of(), new byte[0]));

        assertThat(reply).isInstanceOf(ProxyMessage.Failure.class);
        assertThat(((ProxyMessage.Failure) reply).code()).isEqualTo("BAD_GATEWAY");
    }
}
