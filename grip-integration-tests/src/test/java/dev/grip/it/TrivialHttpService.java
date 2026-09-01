package dev.grip.it;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal internal HTTP service, standing in for whatever a real Agent would
 * expose. Used by the end-to-end tests as the far side of the proxy chain:
 *
 * <pre>
 *   HTTP client -> Controller -> Agent -> TrivialHttpService
 * </pre>
 *
 * <ul>
 *   <li>{@code GET /status} → {@code 200 "ok"}</li>
 *   <li>{@code POST /echo} → {@code 200} with the request body echoed back</li>
 *   <li>{@code GET /slow?ms=N} → {@code 200 "ok"} after an N ms delay
 *       (for cancellation tests)</li>
 * </ul>
 */
public final class TrivialHttpService implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger active = new AtomicInteger();

    private TrivialHttpService(HttpServer server) {
        this.server = server;
    }

    public static TrivialHttpService start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        TrivialHttpService service = new TrivialHttpService(server);
        server.createContext("/status", exchange -> respond(exchange, 200, "ok"));
        server.createContext("/echo", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/slow", exchange -> {
            long ms = queryMillis(exchange.getRequestURI().getQuery());
            service.active.incrementAndGet();
            try {
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                long deadline = System.currentTimeMillis() + ms;
                // Trickle so a client disconnect (a cancelled internal call)
                // is noticed promptly instead of after the full delay.
                while (System.currentTimeMillis() < deadline) {
                    os.write(' ');
                    os.flush();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                os.write("done".getBytes(StandardCharsets.UTF_8));
                os.close();
            } catch (IOException disconnected) {
                // client went away — fine
            } finally {
                service.active.decrementAndGet();
                exchange.close();
            }
        });
        server.start();
        return service;
    }

    /** Number of requests currently being served (used by cancellation tests). */
    public int activeRequests() {
        return active.get();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static long queryMillis(String query) {
        if (query == null) {
            return 0;
        }
        for (String pair : query.split("&")) {
            if (pair.startsWith("ms=")) {
                try {
                    return Long.parseLong(pair.substring(3));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
