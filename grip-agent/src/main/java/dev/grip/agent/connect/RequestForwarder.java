package dev.grip.agent.connect;

import dev.grip.protocol.wire.ProxyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Forwards one proxied request to the Agent's single configured internal
 * service and turns the result back into a {@link ProxyMessage}.
 *
 * <p>Stage 2: request and response bodies are buffered. Streaming is Stage 5.
 * Header handling is minimal — enough not to break the internal call; proper
 * hop-by-hop / forwarding-header hygiene is Stage 8.
 */
public final class RequestForwarder {

    private static final Logger log = LoggerFactory.getLogger(RequestForwarder.class);

    /** Headers the client must not carry across a hop, plus ones the JDK client manages itself. */
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

    public ProxyMessage forward(ProxyMessage.Request request) {
        try {
            URI uri = targetBase.resolve(request.target());
            byte[] body = request.body();
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .method(request.method(), body.length == 0
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(body));
            request.headers().forEach((name, values) -> {
                if (!DROP.contains(name.toLowerCase(Locale.ROOT))) {
                    values.forEach(v -> builder.header(name, v));
                }
            });

            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new ProxyMessage.Response(request.channel(), response.statusCode(),
                    responseHeaders(response.headers().map()), response.body());
        } catch (Exception e) {
            log.warn("internal request to {} failed on channel {}: {}",
                    targetBase, request.channel(), e.toString());
            return new ProxyMessage.Failure(request.channel(), "BAD_GATEWAY",
                    e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
    }

    private static Map<String, List<String>> responseHeaders(Map<String, List<String>> raw) {
        Map<String, List<String>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        raw.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!DROP.contains(lower) && !lower.startsWith(":")) {
                out.put(name, List.copyOf(values));
            }
        });
        return out;
    }
}
