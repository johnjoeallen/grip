package dev.grip.protocol.wire;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyCodecTest {

    @Test
    void roundTripsARequestWithHeadersAndBody() {
        ProxyMessage.Request in = new ProxyMessage.Request(1001, "POST", "/echo?x=1",
                Map.of("Content-Type", List.of("application/json"), "X-A", List.of("1", "2")),
                "hello".getBytes(StandardCharsets.UTF_8));

        ProxyMessage out = ProxyCodec.decode(ProxyCodec.encode(in));

        assertThat(out).isInstanceOf(ProxyMessage.Request.class);
        ProxyMessage.Request r = (ProxyMessage.Request) out;
        assertThat(r.channel()).isEqualTo(1001);
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.target()).isEqualTo("/echo?x=1");
        assertThat(r.headers()).containsEntry("X-A", List.of("1", "2"));
        assertThat(new String(r.body(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void roundTripsAResponseAndAFailure() {
        ProxyMessage.Response resp = new ProxyMessage.Response(7, 204, Map.of(), new byte[0]);
        assertThat(ProxyCodec.decode(ProxyCodec.encode(resp))).isEqualTo(resp);

        ProxyMessage.Failure fail = new ProxyMessage.Failure(7, "BAD_GATEWAY", "connection refused");
        assertThat(ProxyCodec.decode(ProxyCodec.encode(fail))).isEqualTo(fail);
    }

    @Test
    void distinguishesProxyMessagesFromControlFrames() {
        assertThat(ProxyCodec.isProxyMessage(ProxyCodec.encode(
                new ProxyMessage.Failure(1, "X", "y")))).isTrue();
        assertThat(ProxyCodec.isProxyMessage("REGISTER alpha 0")).isFalse();
        assertThat(ProxyCodec.isProxyMessage("PING")).isFalse();
        assertThat(ProxyCodec.isProxyMessage("")).isFalse();
    }

    @Test
    void carriesArbitraryBinaryBodies() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            bytes[i] = (byte) i;
        }
        ProxyMessage.Response resp = new ProxyMessage.Response(1, 200, Map.of(), bytes);
        ProxyMessage.Response back = (ProxyMessage.Response) ProxyCodec.decode(ProxyCodec.encode(resp));
        assertThat(back.body()).containsExactly(bytes);
    }
}
