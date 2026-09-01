package dev.grip.agent.connect;

import com.sun.net.httpserver.HttpServer;
import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.Headers;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ForwardingChannelTest {

    private HttpServer server;
    private RequestForwarder forwarder;
    private final List<Frame> emitted = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", ex -> respond(ex, 200, "ok"));
        server.createContext("/echo", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200, "late");
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

    private ForwardingChannel channel(long id) {
        return new ForwardingChannel(id, forwarder, emitted::add);
    }

    @Test
    void forwardsAGetAndEmitsResponseFrames() {
        ForwardingChannel c = channel(1);
        c.onStart(new Frame.RequestStart(1, "GET", "/status", Headers.of()));
        c.onEnd();

        await().atMost(Duration.ofSeconds(5)).until(() ->
                emitted.stream().anyMatch(f -> f instanceof Frame.ResponseEnd));

        assertThat(emitted.get(0)).isInstanceOf(Frame.ResponseStart.class);
        assertThat(((Frame.ResponseStart) emitted.get(0)).status()).isEqualTo(200);
        Frame.ResponseData data = (Frame.ResponseData) emitted.get(1);
        assertThat(new String(data.data(), StandardCharsets.UTF_8)).isEqualTo("ok");
    }

    @Test
    void forwardsAPostBody() {
        ForwardingChannel c = channel(2);
        c.onStart(new Frame.RequestStart(2, "POST", "/echo", Headers.of()));
        c.onData(new Frame.RequestData(2, "round trip".getBytes(StandardCharsets.UTF_8)));
        c.onEnd();

        await().atMost(Duration.ofSeconds(5)).until(() ->
                emitted.stream().anyMatch(f -> f instanceof Frame.ResponseEnd));

        Frame.ResponseData data = (Frame.ResponseData) emitted.get(1);
        assertThat(new String(data.data(), StandardCharsets.UTF_8)).isEqualTo("round trip");
    }

    @Test
    void emitsErrorWhenTheServiceIsUnreachable() {
        RequestForwarder dead = new RequestForwarder(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:1"), Duration.ofSeconds(2));
        ForwardingChannel c = new ForwardingChannel(3, dead, emitted::add);

        c.onStart(new Frame.RequestStart(3, "GET", "/", Headers.of()));
        c.onEnd();

        await().atMost(Duration.ofSeconds(5)).until(() ->
                emitted.stream().anyMatch(f -> f instanceof Frame.Error));
        Frame.Error error = (Frame.Error) emitted.stream().filter(f -> f instanceof Frame.Error).findFirst().orElseThrow();
        assertThat(error.channel()).isEqualTo(3);
    }

    @Test
    void cancelStopsAnyFurtherFrames() throws Exception {
        ForwardingChannel c = channel(4);
        c.onStart(new Frame.RequestStart(4, "GET", "/slow", Headers.of()));
        c.onEnd();
        Thread.sleep(200);

        c.cancel();
        Thread.sleep(1800); // past when /slow would have answered

        assertThat(emitted).noneMatch(f -> f instanceof Frame.ResponseStart || f instanceof Frame.ResponseEnd);
    }
}
