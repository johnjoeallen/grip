package dev.grip.protocol.wire;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Reassembles GRIP frames from a byte stream that may deliver partial frames or
 * several frames at once. Not thread-safe; drive it from one reader thread.
 *
 * <p>Over a WebSocket, each binary message is already a whole frame, so
 * {@link FrameCodec#decode(byte[])} is enough. This is for a raw stream carrier
 * and keeps the frame format honestly carrier-independent.
 */
public final class FrameDecoder {

    private byte[] buffer = new byte[0];

    /** Append received bytes. */
    public void offer(byte[] chunk) {
        if (chunk.length == 0) {
            return;
        }
        byte[] grown = new byte[buffer.length + chunk.length];
        System.arraycopy(buffer, 0, grown, 0, buffer.length);
        System.arraycopy(chunk, 0, grown, buffer.length, chunk.length);
        buffer = grown;
    }

    /** The next complete frame, or empty if more bytes are needed. */
    public Optional<Frame> poll() {
        if (buffer.length < FrameCodec.HEADER_BYTES) {
            return Optional.empty();
        }
        ByteBuffer view = ByteBuffer.wrap(buffer);
        Frame frame;
        try {
            frame = FrameCodec.decode(view);
        } catch (BufferUnderflowException e) {
            return Optional.empty();
        }
        int consumed = view.position();
        byte[] rest = new byte[buffer.length - consumed];
        System.arraycopy(buffer, consumed, rest, 0, rest.length);
        buffer = rest;
        return Optional.of(frame);
    }

    public int buffered() {
        return buffer.length;
    }
}
