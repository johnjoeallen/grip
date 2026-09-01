package dev.grip.controller.route;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Minimal {@code application/problem+json} writer (RFC 9457 shape). */
final class ProblemResponse {

    private ProblemResponse() {
    }

    static void write(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        String body = "{\"title\":\"" + esc(title) + "\",\"status\":" + status
                + ",\"detail\":\"" + esc(detail) + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
