package dev.grip.agent.connect;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G1.2: the Agent uses the JDK default trust manager and has no insecure mode.
 * Pointed at a server whose certificate is not trusted, {@link
 * AgentConnection#connect()} must fail and the connection must not become
 * REGISTERED.
 */
class AgentConnectionTlsTest {

    private HttpsServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void refusesAnUntrustedCertificateWithNoInsecureFallback() throws Exception {
        int port = startHttpsWithUntrustedCert();

        // A plain default client — exactly what the Agent builds (no sslContext()).
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        AgentConnection connection = new AgentConnection(
                client, URI.create("https://localhost:" + port), "alpha",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(45));

        assertThatThrownBy(() -> connection.connect())
                .satisfiesAnyOf(
                        e -> assertThat(e).hasStackTraceContaining("SSLHandshakeException"),
                        e -> assertThat(e).hasStackTraceContaining("unable to find valid certification path"),
                        e -> assertThat(e).hasStackTraceContaining("PKIX"));

        assertThat(connection.state()).isEqualTo(ConnectionState.DISCONNECTED);
    }

    private int startHttpsWithUntrustedCert() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream("/tls/untrusted-keystore.p12")) {
            ks.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }
}
