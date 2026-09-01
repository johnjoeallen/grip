package dev.grip.controller.connect;

import dev.grip.protocol.wire.RegisterRejectReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRegistryTest {

    private final AgentRegistry registry = new AgentRegistry();

    private AgentConnection conn() {
        return new AgentConnection("test", new FrameSink() {
            public void send(dev.grip.protocol.wire.Frame frame) { }
            public void close() { }
        });
    }

    @Test
    void registersAndTracksTwoAgents() {
        AgentConnection a = conn();
        AgentConnection b = conn();

        assertThat(registry.register("alpha", a)).isInstanceOf(AgentRegistry.Result.Registered.class);
        assertThat(registry.register("beta", b)).isInstanceOf(AgentRegistry.Result.Registered.class);

        assertThat(registry.connectedIds()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(registry.get("alpha")).contains(a);
    }

    @Test
    void rejectsADuplicateWhileTheIncumbentIsConnected() {
        registry.register("alpha", conn());

        AgentRegistry.Result second = registry.register("alpha", conn());

        assertThat(second).isEqualTo(new AgentRegistry.Result.Rejected(RegisterRejectReason.DUPLICATE_AGENT_ID));
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void allowsReRegistrationOnceTheIncumbentIsClosed() {
        AgentConnection first = conn();
        registry.register("alpha", first);
        first.close("gone");

        AgentConnection second = conn();
        assertThat(registry.register("alpha", second)).isInstanceOf(AgentRegistry.Result.Registered.class);
        assertThat(registry.get("alpha")).contains(second);
    }

    @Test
    void rejectsReservedAndMalformedIds() {
        assertThat(registry.register("api", conn()))
                .isEqualTo(new AgentRegistry.Result.Rejected(RegisterRejectReason.RESERVED_AGENT_ID));
        assertThat(registry.register("Not Valid", conn()))
                .isEqualTo(new AgentRegistry.Result.Rejected(RegisterRejectReason.MALFORMED));
        assertThat(registry.register(null, conn()))
                .isEqualTo(new AgentRegistry.Result.Rejected(RegisterRejectReason.MALFORMED));
    }

    @Test
    void unregisterOnlyRemovesTheCurrentHolder() {
        AgentConnection first = conn();
        registry.register("alpha", first);
        first.close("closed");
        AgentConnection second = conn();
        registry.register("alpha", second);

        registry.unregister(first); // stale — must not evict "second"

        assertThat(registry.get("alpha")).contains(second);
    }
}
