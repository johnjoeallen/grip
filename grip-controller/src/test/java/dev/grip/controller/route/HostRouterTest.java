package dev.grip.controller.route;

import dev.grip.controller.GripControllerProperties;
import dev.grip.controller.connect.AgentConnection;
import dev.grip.controller.connect.AgentRegistry;
import dev.grip.controller.connect.FrameSink;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HostRouterTest {

    private final AgentRegistry registry = new AgentRegistry();
    private final HostRouter router = new HostRouter(registry,
            new GripControllerProperties("grip.example.com", null));

    private AgentConnection connect(String id) {
        AgentConnection c = new AgentConnection("test", new FrameSink() {
            public void send(String line) { }
            public void close() { }
        });
        registry.register(id, c);
        return c;
    }

    @Test
    void extractsTheAgentLabelUnderTheBaseDomain() {
        assertThat(router.agentIdOf("alpha.grip.example.com")).isEqualTo("alpha");
        assertThat(router.agentIdOf("ALPHA.GRIP.EXAMPLE.COM:443")).isEqualTo("alpha");
        assertThat(router.agentIdOf("alpha.grip.example.com.")).isEqualTo("alpha");
    }

    @Test
    void rejectsHostsThatAreNotASingleLabelUnderTheBaseDomain() {
        assertThat(router.agentIdOf("grip.example.com")).isNull();          // no label
        assertThat(router.agentIdOf("a.b.grip.example.com")).isNull();      // two labels
        assertThat(router.agentIdOf("alpha.grip.example.org")).isNull();    // wrong base
        assertThat(router.agentIdOf("example.com")).isNull();
        assertThat(router.agentIdOf(null)).isNull();
        assertThat(router.isAgentHost("controller.internal")).isFalse();
    }

    @Test
    void resolvesAConnectedAgent() {
        AgentConnection alpha = connect("alpha");
        assertThat(router.resolve("alpha.grip.example.com"))
                .isEqualTo(new HostRouter.Route.Resolved("alpha", alpha));
    }

    @Test
    void reportsUnknownDisconnectedAndNonAgentHosts() {
        assertThat(router.resolve("ghost.grip.example.com"))
                .isEqualTo(new HostRouter.Route.UnknownAgent("ghost"));

        AgentConnection beta = connect("beta");
        beta.close("gone");
        assertThat(router.resolve("beta.grip.example.com"))
                .isEqualTo(new HostRouter.Route.AgentDisconnected("beta"));

        assertThat(router.resolve("not-us.example.org"))
                .isInstanceOf(HostRouter.Route.NotAnAgentHost.class);
    }
}
