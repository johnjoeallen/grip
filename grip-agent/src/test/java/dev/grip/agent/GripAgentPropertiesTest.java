package dev.grip.agent;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GripAgentPropertiesTest {

    @Test
    void rejectsAPlainHttpControllerUrl() {
        assertThatThrownBy(() -> new GripAgentProperties(
                "alpha", URI.create("http://controller.example"), URI.create("http://localhost:8080"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void fillsInReconnectDefaults() {
        GripAgentProperties p = new GripAgentProperties(
                "alpha", URI.create("https://controller.example"), URI.create("http://localhost:8080"), null);
        assertThat(p.reconnect().initialBackoff()).isNotNull();
        assertThat(p.reconnect().maxBackoff()).isNotNull();
    }
}
