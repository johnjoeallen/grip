package dev.grip.controller.connect;

import dev.grip.controller.GripControllerProperties;
import dev.grip.protocol.wire.Frame;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DeadAgentReaperTest {

    private final AgentRegistry registry = new AgentRegistry();
    private final AgentConnectHandler handler = new AgentConnectHandler(registry);
    private final DeadAgentReaper reaper = new DeadAgentReaper(registry, handler,
            new GripControllerProperties("grip.test",
                    new GripControllerProperties.Connect(Duration.ofSeconds(15), Duration.ofSeconds(30))));

    private AgentConnection register(String id) {
        AgentConnection c = new AgentConnection("test", new FrameSink() {
            public void send(Frame frame) { }
            public void close() { }
        });
        handler.register(c, new Frame.Register(0, id));
        return c;
    }

    @Test
    void closesAndUnregistersASilentAgent() {
        AgentConnection quiet = register("alpha");
        AgentConnection lively = register("beta");
        quiet.markStaleForTest();

        reaper.reap();

        assertThat(quiet.state()).isEqualTo(AgentConnection.State.CLOSED);
        assertThat(registry.get("alpha")).isEmpty();
        assertThat(registry.get("beta")).contains(lively);
    }

    @Test
    void leavesAFreshAgentAlone() {
        AgentConnection c = register("alpha");
        reaper.reap();
        assertThat(c.state()).isEqualTo(AgentConnection.State.REGISTERED);
    }
}
