package dev.grip.protocol;

/**
 * Identifies one multiplexed channel (one in-flight external request) on a GRIP
 * connection. Allocated by the Controller, unique for the lifetime of the
 * connection, and may be reclaimed after the channel closes.
 *
 * <p>The width and allocation strategy of the underlying value are a protocol
 * detail still to be fixed; {@code long} is a placeholder that is comfortably
 * wide enough and cheap to compare.
 */
public record ChannelId(long value) implements Comparable<ChannelId> {

    public ChannelId {
        if (value < 0) {
            throw new IllegalArgumentException("channel id must be non-negative: " + value);
        }
    }

    public static ChannelId of(long value) {
        return new ChannelId(value);
    }

    @Override
    public int compareTo(ChannelId other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return "channel " + value;
    }
}
