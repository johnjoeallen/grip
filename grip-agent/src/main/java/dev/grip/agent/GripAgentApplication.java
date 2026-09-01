package dev.grip.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * GRIP Agent entry point.
 *
 * <p>The Agent runs inside a private network. It opens a single long-lived
 * outbound TLS connection to the Controller, then forwards the requests the
 * Controller sends over that connection to one configured internal HTTP
 * service. Nothing connects <em>to</em> the Agent.
 *
 * <p>This is currently a skeleton: it boots, exposes a localhost health
 * endpoint, and reads its configuration. The outbound connection and proxying
 * are built up over the staged issues.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GripAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(GripAgentApplication.class, args);
    }
}
