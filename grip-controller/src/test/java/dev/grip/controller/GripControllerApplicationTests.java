package dev.grip.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GripControllerApplicationTests {

    @Autowired
    GripControllerProperties properties;

    @Test
    void contextLoadsAndBindsConfiguration() {
        assertThat(properties.baseDomain()).isEqualTo("grip.example.com");
        assertThat(properties.connect().agentTimeout()).isEqualTo(java.time.Duration.ofSeconds(45));
    }
}
