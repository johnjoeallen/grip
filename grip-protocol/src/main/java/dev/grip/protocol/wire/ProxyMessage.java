package dev.grip.protocol.wire;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <strong>Provisional</strong> proxy framing for the Stage 2 vertical slice:
 * one request and one response per channel, each carried whole in a single
 * WebSocket message. Bodies are buffered and base64-encoded in JSON.
 *
 * <p>This exists only to get one request flowing end to end. Stage 3 replaces
 * it with a streamed binary frame format (`REQUEST_START` / `*_DATA` / …), and
 * bodies stop being base64. Do not build streaming or multiplexing on this.
 */
public sealed interface ProxyMessage {

    long channel();

    /** An external request, Controller → Agent. {@code target} is path + query. */
    record Request(long channel, String method, String target,
                   Map<String, List<String>> headers, byte[] body) implements ProxyMessage {
        public Request {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? new byte[0] : body;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Request r && channel == r.channel && Objects.equals(method, r.method)
                    && Objects.equals(target, r.target) && Objects.equals(headers, r.headers)
                    && Arrays.equals(body, r.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, method, target, headers) * 31 + Arrays.hashCode(body);
        }
    }

    /** The internal service's response, Agent → Controller. */
    record Response(long channel, int status,
                    Map<String, List<String>> headers, byte[] body) implements ProxyMessage {
        public Response {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? new byte[0] : body;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Response r && channel == r.channel && status == r.status
                    && Objects.equals(headers, r.headers) && Arrays.equals(body, r.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, status, headers) * 31 + Arrays.hashCode(body);
        }
    }

    /** The channel failed on the Agent side (internal service unreachable, etc.). */
    record Failure(long channel, String code, String message) implements ProxyMessage {
    }
}
