package dev.grip.protocol.wire;

/** Why a channel was cancelled. Carried as the {@code u8} payload of {@code CANCEL}. */
public enum CancelReason {

    UNSPECIFIED(0),
    /** The external client disconnected. */
    CLIENT_GONE(1),
    /** The Agent's internal service failed or went away. */
    SERVICE_FAILED(2),
    /** One side is shutting down. */
    SHUTDOWN(3);

    private final int code;

    CancelReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static CancelReason fromCode(int code) {
        for (CancelReason r : values()) {
            if (r.code == code) {
                return r;
            }
        }
        return UNSPECIFIED;
    }
}
