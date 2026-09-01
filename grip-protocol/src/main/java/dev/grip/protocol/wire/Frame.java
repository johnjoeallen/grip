package dev.grip.protocol.wire;

import dev.grip.protocol.FrameType;

import java.util.Arrays;
import java.util.Objects;

/**
 * A decoded GRIP frame. See {@code docs/protocol.md} for the wire format and
 * {@link FrameCodec} for the encoding.
 */
public sealed interface Frame {

    FrameType type();

    /** The channel this frame belongs to; {@code 0} for connection-scoped frames. */
    long channel();

    // ---- connection-scoped ----

    record Register(int protocolVersion, String agentId) implements Frame {
        public FrameType type() {
            return FrameType.REGISTER;
        }

        public long channel() {
            return 0;
        }
    }

    record RegisterOk() implements Frame {
        public FrameType type() {
            return FrameType.REGISTER_OK;
        }

        public long channel() {
            return 0;
        }
    }

    record RegisterRejected(RegisterRejectReason reason) implements Frame {
        public FrameType type() {
            return FrameType.REGISTER_REJECTED;
        }

        public long channel() {
            return 0;
        }
    }

    record Ping(long nonce) implements Frame {
        public FrameType type() {
            return FrameType.PING;
        }

        public long channel() {
            return 0;
        }
    }

    record Pong(long nonce) implements Frame {
        public FrameType type() {
            return FrameType.PONG;
        }

        public long channel() {
            return 0;
        }
    }

    // ---- channel-scoped ----

    record RequestStart(long channel, String method, String target, Headers headers) implements Frame {
        public FrameType type() {
            return FrameType.REQUEST_START;
        }
    }

    record RequestData(long channel, byte[] data) implements Frame {
        public FrameType type() {
            return FrameType.REQUEST_DATA;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RequestData d && channel == d.channel && Arrays.equals(data, d.data);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(channel) * 31 + Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "RequestData[channel=" + channel + ", data=" + data.length + "B]";
        }
    }

    record RequestEnd(long channel) implements Frame {
        public FrameType type() {
            return FrameType.REQUEST_END;
        }
    }

    record ResponseStart(long channel, int status, Headers headers) implements Frame {
        public FrameType type() {
            return FrameType.RESPONSE_START;
        }
    }

    record ResponseData(long channel, byte[] data) implements Frame {
        public FrameType type() {
            return FrameType.RESPONSE_DATA;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ResponseData d && channel == d.channel && Arrays.equals(data, d.data);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(channel) * 31 + Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "ResponseData[channel=" + channel + ", data=" + data.length + "B]";
        }
    }

    record ResponseEnd(long channel) implements Frame {
        public FrameType type() {
            return FrameType.RESPONSE_END;
        }
    }

    record Cancel(long channel, CancelReason reason) implements Frame {
        public Cancel {
            reason = Objects.requireNonNullElse(reason, CancelReason.UNSPECIFIED);
        }

        public FrameType type() {
            return FrameType.CANCEL;
        }
    }

    record Error(long channel, ErrorCode code, String message) implements Frame {
        public Error {
            code = Objects.requireNonNullElse(code, ErrorCode.INTERNAL);
            message = message == null ? "" : message;
        }

        public FrameType type() {
            return FrameType.ERROR;
        }
    }
}
