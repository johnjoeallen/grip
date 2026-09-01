package dev.grip.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameTypeTest {

    @Test
    void pingAndPongAreNotChannelScoped() {
        assertThat(FrameType.PING.isChannelScoped()).isFalse();
        assertThat(FrameType.PONG.isChannelScoped()).isFalse();
    }

    @Test
    void everyDataPlaneFrameIsChannelScoped() {
        for (FrameType type : FrameType.values()) {
            if (type == FrameType.PING || type == FrameType.PONG) {
                continue;
            }
            assertThat(type.isChannelScoped()).as(type.name()).isTrue();
        }
    }

    @Test
    void channelIdRejectsNegativeValues() {
        assertThat(ChannelId.of(1001).value()).isEqualTo(1001L);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ChannelId.of(-1));
    }
}
