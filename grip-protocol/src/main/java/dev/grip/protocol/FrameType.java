package dev.grip.protocol;

/**
 * The kinds of frame that travel over a GRIP connection, and their wire type
 * byte. See {@code docs/protocol.md} for the full frame format.
 *
 * <p>A GRIP connection carries many independent <em>channels</em>, one per
 * in-flight external HTTP request. Channel-scoped frames name their channel;
 * connection-scoped frames ({@link #REGISTER}, {@link #REGISTER_OK},
 * {@link #REGISTER_REJECTED}, {@link #PING}, {@link #PONG}) use channel 0.
 */
public enum FrameType {

    REGISTER(0x01, false),
    REGISTER_OK(0x02, false),
    REGISTER_REJECTED(0x03, false),

    REQUEST_START(0x10, true),
    REQUEST_DATA(0x11, true),
    REQUEST_END(0x12, true),

    RESPONSE_START(0x20, true),
    RESPONSE_DATA(0x21, true),
    RESPONSE_END(0x22, true),

    CANCEL(0x30, true),
    ERROR(0x31, true),

    PING(0x40, false),
    PONG(0x41, false);

    private final int wire;
    private final boolean channelScoped;

    FrameType(int wire, boolean channelScoped) {
        this.wire = wire;
        this.channelScoped = channelScoped;
    }

    /** The single byte identifying this type on the wire. */
    public byte wire() {
        return (byte) wire;
    }

    /** Whether a frame of this type is bound to a single channel. */
    public boolean isChannelScoped() {
        return channelScoped;
    }

    public static FrameType fromWire(byte b) {
        for (FrameType t : values()) {
            if (t.wire == (b & 0xff)) {
                return t;
            }
        }
        return null;
    }
}
