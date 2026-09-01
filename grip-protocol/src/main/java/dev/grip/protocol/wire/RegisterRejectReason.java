package dev.grip.protocol.wire;

/** Why a {@code REGISTER} was refused. Carried as the {@code u8} payload of {@code REGISTER_REJECTED}. */
public enum RegisterRejectReason {

    /** The REGISTER frame was missing fields or otherwise unparseable. */
    MALFORMED(0),

    /** The Agent's protocol version is not supported by this Controller. */
    UNSUPPORTED_VERSION(1),

    /** An Agent with this id is already connected. */
    DUPLICATE_AGENT_ID(2),

    /** The requested agent id is reserved and cannot be used. */
    RESERVED_AGENT_ID(3);

    private final int code;

    RegisterRejectReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static RegisterRejectReason fromCode(int code) {
        for (RegisterRejectReason r : values()) {
            if (r.code == code) {
                return r;
            }
        }
        return MALFORMED;
    }

    /** Lenient name lookup, kept for the provisional line framing. */
    public static RegisterRejectReason from(String token) {
        try {
            return valueOf(token);
        } catch (IllegalArgumentException | NullPointerException e) {
            return MALFORMED;
        }
    }
}
