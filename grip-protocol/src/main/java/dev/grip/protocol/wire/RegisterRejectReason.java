package dev.grip.protocol.wire;

/** Why a {@code REGISTER} was refused. Sent as the single arg of {@code REGISTER_REJECTED}. */
public enum RegisterRejectReason {

    /** An Agent with this id is already connected. */
    DUPLICATE_AGENT_ID,

    /** The Agent's protocol version is not supported by this Controller. */
    UNSUPPORTED_VERSION,

    /** The REGISTER frame was missing fields or otherwise unparseable. */
    MALFORMED,

    /** The requested agent id is reserved and cannot be used. */
    RESERVED_AGENT_ID;

    public static RegisterRejectReason from(String token) {
        try {
            return valueOf(token);
        } catch (IllegalArgumentException | NullPointerException e) {
            return MALFORMED;
        }
    }
}
