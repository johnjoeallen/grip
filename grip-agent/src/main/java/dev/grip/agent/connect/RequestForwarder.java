package dev.grip.agent.connect;

import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.Headers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Sends one proxied request to the Agent's single configured internal service.
 * Returns a cancellable future so an inbound {@code CANCEL} can abort the
 * internal call.
 *
 * <p>Stage 3: request and response bodies are buffered. Streaming is Stage 5.
 * Proper hop-by-hop / forwarding-header hygiene is Stage 8.
 */
public final class RequestForwarder {

    private static final Set<String> DROP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade",
            "host", "content-length", "expect");

    private final HttpClient http;
    private final URI targetBase;
    private final Duration timeout;

    public RequestForwarder(HttpClient http, URI targetBase, Duration timeout) {
        this.http = http;
        this.targetBase = targetBase;
        this.timeout = timeout;
    }

    public CompletableFuture<HttpResponse<byte[]>> send(Frame.RequestStart start, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(targetBase.resolve(start.target()))
                .timeout(timeout)
                .method(start.method(), body.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        start.headers().forEach((name, value) -> {
            if (!DROP.contains(name.toLowerCase(Locale.ROOT))) {
                builder.header(name, value);
            }
        });
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    static Headers responseHeaders(java.util.Map<String, java.util.List<String>> raw) {
        Headers headers = new Headers();
        raw.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!DROP.contains(lower) && !lower.startsWith(":")) {
                values.forEach(v -> headers.add(name, v));
            }
        });
        return headers;
    }
}
