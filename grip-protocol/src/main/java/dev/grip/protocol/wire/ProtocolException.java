package dev.grip.protocol.wire;

/**
 * A malformed or over-limit frame. The reader treats this as connection-fatal
 * (see {@code docs/protocol.md} — "Malformed input").
 */
public class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }
}
