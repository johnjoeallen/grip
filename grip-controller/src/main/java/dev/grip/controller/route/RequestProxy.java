package dev.grip.controller.route;

import dev.grip.controller.connect.AgentConnection;
import dev.grip.controller.connect.ProxyExchange;
import dev.grip.protocol.wire.CancelReason;
import dev.grip.protocol.wire.ErrorCode;
import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.Headers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Proxies one external request over a resolved Agent connection as GRIP frames
 * and writes the reply to the external response.
 *
 * <p>Stage 3: one in-flight request per Agent (a second gets {@code 503}),
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
                    "Agent '" + agentId + "' is already handling a request (Stage 3 is single-channel).");
            return;
        }
        ProxyExchange exchange = claimed.get();
        long channel = exchange.channel();
        try {
            byte[] body = request.getInputStream().readAllBytes();
            connection.send(new Frame.RequestStart(channel, request.getMethod(),
                    pathAndQuery(request), requestHeaders(request)));
            if (body.length > 0) {
                connection.send(new Frame.RequestData(channel, body));
            }
            connection.send(new Frame.RequestEnd(channel));

            ProxyExchange.Result result = exchange.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            writeResult(response, agentId, result);
        } catch (TimeoutException e) {
            connection.send(new Frame.Cancel(channel, CancelReason.UNSPECIFIED));
            ProblemResponse.write(response, 504, "Gateway timeout",
                    "Agent '" + agentId + "' did not respond within " + TIMEOUT_SECONDS + "s.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connection.send(new Frame.Cancel(channel, CancelReason.SHUTDOWN));
            ProblemResponse.write(response, 502, "Bad gateway", "interrupted");
        } catch (IOException e) {
            // Writing to the client failed — it went away.
            connection.cancelInFlight(CancelReason.CLIENT_GONE);
            log.debug("client gone on channel {}: {}", channel, e.toString());
        } catch (Exception e) {
            log.warn("proxy to agent {} failed on channel {}: {}", agentId, channel, e.toString());
            ProblemResponse.write(response, 502, "Bad gateway", rootMessage(e));
        } finally {
            connection.endExchange(channel);
        }
    }

    private void writeResult(HttpServletResponse response, String agentId, ProxyExchange.Result result)
            throws IOException {
        switch (result) {
            case ProxyExchange.Result.Ok ok -> writeOk(response, ok);
            case ProxyExchange.Result.Failed failed -> {
                int status = failed.code() == ErrorCode.GATEWAY_TIMEOUT ? 504 : 502;
                ProblemResponse.write(response, status,
                        status == 504 ? "Gateway timeout" : "Bad gateway", failed.message());
            }
            case ProxyExchange.Result.Cancelled cancelled -> ProblemResponse.write(response, 502, "Bad gateway",
                    "Agent '" + agentId + "' cancelled the request (" + cancelled.reason() + ").");
        }
    }

    private void writeOk(HttpServletResponse response, ProxyExchange.Result.Ok ok) throws IOException {
        response.setStatus(ok.status());
        ok.headers().forEach((name, value) -> {
            if (!DROP_RESPONSE.contains(name.toLowerCase(Locale.ROOT))) {
                response.addHeader(name, value);
            }
        });
        response.setContentLength(ok.body().length);
        response.getOutputStream().write(ok.body());
    }

    private static String pathAndQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return request.getQueryString() == null ? uri : uri + "?" + request.getQueryString();
    }

    private static Headers requestHeaders(HttpServletRequest request) {
        Headers headers = new Headers();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (DROP_REQUEST.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String value : Collections.list(request.getHeaders(name))) {
                headers.add(name, value);
            }
        }
        return headers;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
