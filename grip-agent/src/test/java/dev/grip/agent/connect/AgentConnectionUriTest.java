package dev.grip.agent.connect;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentConnectionUriTest {

    @Test
    void mapsHttpsToWssAndAppendsTheConnectPath() {
        assertThat(AgentConnection.toWebSocketUri(URI.create("https://c.grip.test:9443")))
                .hasToString("wss://c.grip.test:9443/grip/connect");
        assertThat(AgentConnection.toWebSocketUri(URI.create("http://localhost:8443")))
                .hasToString("ws://localhost:8443/grip/connect");
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> AgentConnection.toWebSocketUri(URI.create("ftp://x")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
