package dev.grip.protocol;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FrameTypeTest {

    private static final Set<FrameType> CONNECTION_SCOPED = Set.of(
            FrameType.REGISTER, FrameType.REGISTER_OK, FrameType.REGISTER_REJECTED,
            FrameType.PING, FrameType.PONG);

    @Test
    void connectionScopedFramesAreNotChannelScoped() {
        for (FrameType type : CONNECTION_SCOPED) {
            assertThat(type.isChannelScoped()).as(type.name()).isFalse();
        }
    }

    @Test
    void everyOtherFrameIsChannelScoped() {
        for (FrameType type : FrameType.values()) {
            if (!CONNECTION_SCOPED.contains(type)) {
                assertThat(type.isChannelScoped()).as(type.name()).isTrue();
            }
        }
    }

    @Test
    void wireBytesAreUniqueAndRoundTrip() {
        for (FrameType type : FrameType.values()) {
            assertThat(FrameType.fromWire(type.wire())).isEqualTo(type);
        }
        assertThat(FrameType.fromWire((byte) 0x7f)).isNull();
    }

    @Test
    void channelIdRejectsNegativeValues() {
        assertThat(ChannelId.of(1001).value()).isEqualTo(1001L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ChannelId.of(-1));
    }
}
