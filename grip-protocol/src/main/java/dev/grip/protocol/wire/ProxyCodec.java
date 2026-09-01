package dev.grip.protocol.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON codec for the provisional {@link ProxyMessage} framing. One JSON object
 * per WebSocket text message:
 *
 * <pre>
 *   {"t":"req","ch":1001,"method":"GET","target":"/x?y=1","headers":{"Accept":["*"]},"body":"&lt;base64&gt;"}
 *   {"t":"resp","ch":1001,"status":200,"headers":{...},"body":"&lt;base64&gt;"}
 *   {"t":"fail","ch":1001,"code":"BAD_GATEWAY","message":"..."}
 * </pre>
 *
 * Text frames that do not start with '{' are {@link ControlFrame}s, not proxy
 * messages — see {@link #isProxyMessage(String)}.
 */
public final class ProxyCodec {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64E = Base64.getEncoder();
    private static final Base64.Decoder B64D = Base64.getDecoder();

    private ProxyCodec() {
    }

    public static boolean isProxyMessage(String message) {
        return message != null && !message.isBlank() && message.stripLeading().charAt(0) == '{';
    }

    public static String encode(ProxyMessage message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("ch", message.channel());
        switch (message) {
            case ProxyMessage.Request r -> {
                node.put("t", "req");
                node.put("method", r.method());
                node.put("target", r.target());
                node.set("headers", headers(r.headers()));
                node.put("body", B64E.encodeToString(r.body()));
            }
            case ProxyMessage.Response r -> {
                node.put("t", "resp");
                node.put("status", r.status());
                node.set("headers", headers(r.headers()));
                node.put("body", B64E.encodeToString(r.body()));
            }
            case ProxyMessage.Failure f -> {
                node.put("t", "fail");
                node.put("code", f.code());
                node.put("message", f.message());
            }
        }
        try {
            return JSON.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ProxyMessage decode(String message) {
        JsonNode node;
        try {
            node = JSON.readTree(message);
        } catch (IOException e) {
            throw new IllegalArgumentException("unparseable proxy message", e);
        }
        long channel = node.path("ch").asLong();
        return switch (node.path("t").asText()) {
            case "req" -> new ProxyMessage.Request(channel,
                    node.path("method").asText(), node.path("target").asText(),
                    headers(node.path("headers")), body(node));
            case "resp" -> new ProxyMessage.Response(channel,
                    node.path("status").asInt(), headers(node.path("headers")), body(node));
            case "fail" -> new ProxyMessage.Failure(channel,
                    node.path("code").asText(), node.path("message").asText(null));
            default -> throw new IllegalArgumentException("unknown proxy message type: " + node.path("t"));
        };
    }

    private static ObjectNode headers(Map<String, List<String>> headers) {
        ObjectNode node = JSON.createObjectNode();
        headers.forEach((name, values) -> {
            ArrayNode arr = node.putArray(name);
            values.forEach(arr::add);
        });
        return node;
    }

    private static Map<String, List<String>> headers(JsonNode node) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            List<String> values = new ArrayList<>();
            entry.getValue().forEach(v -> values.add(v.asText()));
            out.put(entry.getKey(), values);
        });
        return out;
    }

    private static byte[] body(JsonNode node) {
        String b64 = node.path("body").asText("");
        return b64.isEmpty() ? new byte[0] : B64D.decode(b64);
    }
}
