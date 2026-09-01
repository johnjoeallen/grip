package dev.grip.it;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity check for the fixture itself. The real end-to-end test — client →
 * Controller → Agent → this service — arrives with Stage 2 and is expanded
 * through Stage 4 (multiplexing) and Stage 7 (failure handling).
 */
class TrivialHttpServiceTest {

    @Test
    void servesStatusAndEcho() throws Exception {
        try (TrivialHttpService service = TrivialHttpService.start()) {
            HttpClient http = HttpClient.newHttpClient();

            HttpResponse<String> status = http.send(
                    HttpRequest.newBuilder(URI.create(service.baseUrl() + "/status")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(status.statusCode()).isEqualTo(200);
            assertThat(status.body()).isEqualTo("ok");

            HttpResponse<String> echo = http.send(
                    HttpRequest.newBuilder(URI.create(service.baseUrl() + "/echo"))
                            .POST(HttpRequest.BodyPublishers.ofString("round trip")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(echo.body()).isEqualTo("round trip");
        }
    }
}
