package dev.grip.controller.route;

import dev.grip.controller.connect.AgentConnection;
import dev.grip.protocol.wire.ProxyMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Proxies one external request over a resolved Agent connection and writes the
 * reply to the external response.
 *
 * <p>Stage 2: one in-flight request per Agent (a second gets {@code 503}),
 * bodies buffered. Multiplexing is Stage 4, streaming Stage 5, header hygiene
 * Stage 8.
 */
@Component
public class RequestProxy {

    private static final Logger log = LoggerFactory.getLogger(RequestProxy.class);

    private static final Set<String> DROP_REQUEST = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");
    private static final Set<String> DROP_RESPONSE = Set.of(
            "connection", "keep-alive", "transfer-encoding", "upgrade", "content-length");

    private static final long TIMEOUT_SECONDS = 30;

    public void proxy(HttpServletRequest request, HttpServletResponse response,
            String agentId, AgentConnection connection) throws IOException {

        var claimed = connection.beginExchange();
        if (claimed.isEmpty()) {
            ProblemResponse.write(response, 503, "Agent busy",
                    "Agent '" + agentId + "' is already handling a request (Stage 2 is single-channel).");
            return;
        }
        AgentConnection.Exchange exchange = claimed.get();
        try {
            ProxyMessage.Request forwarded = new ProxyMessage.Request(
                    exchange.channel(), request.getMethod(), pathAndQuery(request),
                    requestHeaders(request), request.getInputStream().readAllBytes());
            connection.sendProxy(forwarded);

            ProxyMessage reply = exchange.reply().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            switch (reply) {
                case ProxyMessage.Response r -> writeResponse(response, r);
                case ProxyMessage.Failure f -> ProblemResponse.write(response, 502, "Bad gateway", f.message());
                default -> ProblemResponse.write(response, 502, "Bad gateway", "unexpected reply from agent");
            }
        } catch (TimeoutException e) {
            ProblemResponse.write(response, 504, "Gateway timeout",
                    "Agent '" + agentId + "' did not respond within " + TIMEOUT_SECONDS + "s.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ProblemResponse.write(response, 502, "Bad gateway", "interrupted");
        } catch (Exception e) {
            log.warn("proxy to agent {} failed on channel {}: {}", agentId, exchange.channel(), e.toString());
            ProblemResponse.write(response, 502, "Bad gateway", rootMessage(e));
        } finally {
            connection.endExchange(exchange.channel());
        }
    }

    private static String pathAndQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return request.getQueryString() == null ? uri : uri + "?" + request.getQueryString();
    }

    private static Map<String, List<String>> requestHeaders(HttpServletRequest request) {
        Map<String, List<String>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (DROP_REQUEST.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.put(name, Collections.list(request.getHeaders(name)));
        }
        return out;
    }

    private static void writeResponse(HttpServletResponse response, ProxyMessage.Response reply) throws IOException {
        response.setStatus(reply.status());
        reply.headers().forEach((name, values) -> {
            if (!DROP_RESPONSE.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(v -> response.addHeader(name, v));
            }
        });
        byte[] body = reply.body();
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
