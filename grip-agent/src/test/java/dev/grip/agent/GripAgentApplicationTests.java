package dev.grip.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "server.port=0")
class GripAgentApplicationTests {

    @Autowired
    GripAgentProperties properties;

    @Test
    void contextLoadsAndBindsConfiguration() {
        assertThat(properties.agentId()).isEqualTo("alpha");
        assertThat(properties.controllerUrl().getScheme()).isEqualTo("https");
        assertThat(properties.reconnect().maxBackoff()).isEqualTo(java.time.Duration.ofSeconds(30));
    }
}
