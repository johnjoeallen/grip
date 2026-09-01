package dev.grip.protocol.wire;

/** The reason a channel ended abnormally. Carried as the {@code u8} payload of {@code ERROR}. */
public enum ErrorCode {

    INTERNAL(0),
    /** The peer sent something that violates the protocol. */
    PROTOCOL(1),
    /** The Agent could not reach or talk to its internal service. */
    BAD_GATEWAY(2),
    /** The internal service did not respond in time. */
    GATEWAY_TIMEOUT(3),
    /** A frame or header block exceeded a limit. */
    TOO_LARGE(4);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ErrorCode fromCode(int code) {
        for (ErrorCode c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        return INTERNAL;
    }
}
