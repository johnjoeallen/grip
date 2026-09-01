package dev.grip.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GRIP Controller entry point.
 *
 * <p>The Controller is the public edge. It terminates external HTTPS, accepts
 * one long-lived outbound connection per Agent, and proxies external requests
 * to the right Agent over a multiplexed channel.
 *
 * <p>This is currently a skeleton: it boots, exposes health, and reads its
 * configuration. Proxying is built up over the staged issues.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GripControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GripControllerApplication.class, args);
    }
}
