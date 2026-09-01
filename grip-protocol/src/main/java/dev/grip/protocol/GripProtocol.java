package dev.grip.protocol;

/**
 * Protocol-wide constants and provisional limits.
 *
 * <p>Everything here is a starting point, not a commitment. The values exist so
 * the skeleton has something concrete to reference; they are expected to be
 * revisited in Stage 5 (streaming and backpressure).
 */
public final class GripProtocol {

    private GripProtocol() {
    }

    /** Bumped on any breaking change to the wire format once one exists. */
    public static final int VERSION = 0;

    /**
     * The HTTP path on the Controller that an Agent opens its long-lived
     * streaming connection to. Provisional.
     */
    public static final String AGENT_CONNECT_PATH = "/grip/connect";

    /** Provisional maximum size of a single frame payload, in bytes. */
    public static final int MAX_FRAME_PAYLOAD_BYTES = 64 * 1024;

    /** Provisional maximum combined size of request/response headers, in bytes. */
    public static final int MAX_HEADER_BYTES = 32 * 1024;

    /** Provisional cap on concurrently open channels per Agent connection. */
    public static final int MAX_CHANNELS_PER_CONNECTION = 1024;
}
