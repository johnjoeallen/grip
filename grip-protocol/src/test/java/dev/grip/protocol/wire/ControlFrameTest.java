package dev.grip.protocol.wire;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlFrameTest {

    @Test
    void roundTripsRegister() {
        ControlFrame f = ControlFrame.of("REGISTER", "alpha", "0");
        assertThat(f.encode()).isEqualTo("REGISTER alpha 0\n");

        ControlFrame parsed = ControlFrame.parse(f.encode().strip());
        assertThat(parsed.verb()).isEqualTo("REGISTER");
        assertThat(parsed.arg(0)).isEqualTo("alpha");
        assertThat(parsed.arg(1)).isEqualTo("0");
        assertThat(parsed.arg(2)).isNull();
    }

    @Test
    void parsesBareVerb() {
        assertThat(ControlFrame.parse("PING").is("ping")).isTrue();
    }

    @Test
    void rejectsEmptyAndBadArgs() {
        assertThatThrownBy(() -> ControlFrame.parse("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ControlFrame.of("X", "a b").encode()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectReasonFallsBackToMalformed() {
        assertThat(RegisterRejectReason.from("DUPLICATE_AGENT_ID")).isEqualTo(RegisterRejectReason.DUPLICATE_AGENT_ID);
        assertThat(RegisterRejectReason.from("nonsense")).isEqualTo(RegisterRejectReason.MALFORMED);
        assertThat(RegisterRejectReason.from(null)).isEqualTo(RegisterRejectReason.MALFORMED);
    }
}
