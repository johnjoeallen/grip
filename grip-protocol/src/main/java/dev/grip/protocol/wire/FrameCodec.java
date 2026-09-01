package dev.grip.protocol.wire;

import dev.grip.protocol.FrameType;
import dev.grip.protocol.GripProtocol;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Encodes and decodes a single GRIP {@link Frame}. See {@code docs/protocol.md}
 * for the byte layout.
 *
 * <p>Frame header: {@code type(u8) flags(u8) channel(u64) length(u32)},
 * big-endian, followed by {@code length} payload bytes.
 */
public final class FrameCodec {

    public static final int HEADER_BYTES = 14;

    private FrameCodec() {
    }

    // ---------------------------------------------------------------- encode

    public static byte[] encode(Frame frame) {
        byte[] payload;
        try {
            payload = payload(frame);
        } catch (java.nio.BufferOverflowException e) {
            throw new ProtocolException(frame.type() + " is too large to encode");
        }
        int max = isStart(frame.type()) ? GripProtocol.MAX_HEADER_BYTES : GripProtocol.MAX_FRAME_PAYLOAD_BYTES;
        if (payload.length > max) {
            throw new ProtocolException(frame.type() + " payload " + payload.length + " exceeds limit " + max);
        }
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + payload.length);
        buffer.put(frame.type().wire());
        buffer.put((byte) 0);                 // flags
        buffer.putLong(frame.channel());
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private static byte[] payload(Frame frame) {
        return switch (frame) {
            case Frame.Register f -> {
                byte[] id = f.agentId().getBytes(StandardCharsets.UTF_8);
                if (id.length > 255) {
                    throw new ProtocolException("agent id too long");
                }
                ByteBuffer b = ByteBuffer.allocate(3 + id.length);
                b.putShort((short) f.protocolVersion());
                b.put((byte) id.length);
                b.put(id);
                yield b.array();
            }
            case Frame.RegisterOk ignored -> new byte[0];
            case Frame.RegisterRejected f -> new byte[] {(byte) f.reason().code()};
            case Frame.RequestStart f -> {
                ByteBuffer b = ByteBuffer.allocate(GripProtocol.MAX_HEADER_BYTES);
                putShortString8(b, f.method());
                putShortString16(b, f.target());
                putHeaders(b, f.headers());
                yield trim(b);
            }
            case Frame.RequestData f -> f.data();
            case Frame.RequestEnd ignored -> new byte[0];
            case Frame.ResponseStart f -> {
                ByteBuffer b = ByteBuffer.allocate(GripProtocol.MAX_HEADER_BYTES);
                b.putShort((short) f.status());
                putHeaders(b, f.headers());
                yield trim(b);
            }
            case Frame.ResponseData f -> f.data();
            case Frame.ResponseEnd ignored -> new byte[0];
            case Frame.Cancel f -> new byte[] {(byte) f.reason().code()};
            case Frame.Error f -> {
                byte[] msg = f.message().getBytes(StandardCharsets.UTF_8);
                if (msg.length > 0xffff) {
                    msg = new String(msg, 0, 0xffff, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
                }
                ByteBuffer b = ByteBuffer.allocate(3 + msg.length);
                b.put((byte) f.code().code());
                b.putShort((short) msg.length);
                b.put(msg);
                yield b.array();
            }
            case Frame.Ping f -> longBytes(f.nonce());
            case Frame.Pong f -> longBytes(f.nonce());
        };
    }

    // ---------------------------------------------------------------- decode

    /** Decodes exactly one frame from {@code bytes}, which must be a whole frame. */
    public static Frame decode(byte[] bytes) {
        return decode(ByteBuffer.wrap(bytes));
    }

    /**
     * Decodes one frame from {@code buffer}, advancing its position past the
     * frame. Throws {@link ProtocolException} on a malformed or over-limit
     * frame; throws {@link BufferUnderflowException} only if the caller passed
     * an incomplete buffer (use {@link FrameDecoder} for a stream).
     */
    public static Frame decode(ByteBuffer buffer) {
        if (buffer.remaining() < HEADER_BYTES) {
            throw new BufferUnderflowException();
        }
        int typeByte = buffer.get() & 0xff;
        int flags = buffer.get() & 0xff;
        long channel = buffer.getLong();
        long length = buffer.getInt() & 0xffffffffL;

        if (flags != 0) {
            throw new ProtocolException("unknown flags 0x" + Integer.toHexString(flags));
        }
        FrameType type = FrameType.fromWire((byte) typeByte);
        if (type == null) {
            throw new ProtocolException("unknown frame type 0x" + Integer.toHexString(typeByte));
        }
        int max = isStart(type) ? GripProtocol.MAX_HEADER_BYTES : GripProtocol.MAX_FRAME_PAYLOAD_BYTES;
        if (length > max) {
            throw new ProtocolException(type + " length " + length + " exceeds limit " + max);
        }
        if (buffer.remaining() < length) {
            throw new BufferUnderflowException();
        }
        if (type.isChannelScoped() == (channel == 0)) {
            throw new ProtocolException(type + " with channel " + channel);
        }

        byte[] payload = new byte[(int) length];
        buffer.get(payload);
        ByteBuffer p = ByteBuffer.wrap(payload);

        try {
            return switch (type) {
                case REGISTER -> {
                    int version = p.getShort() & 0xffff;
                    int idLen = p.get() & 0xff;
                    byte[] id = new byte[idLen];
                    p.get(id);
                    yield new Frame.Register(version, new String(id, StandardCharsets.UTF_8));
                }
                case REGISTER_OK -> new Frame.RegisterOk();
                case REGISTER_REJECTED -> new Frame.RegisterRejected(RegisterRejectReason.fromCode(p.get() & 0xff));
                case REQUEST_START -> new Frame.RequestStart(channel,
                        getShortString8(p), getShortString16(p), getHeaders(p));
                case REQUEST_DATA -> new Frame.RequestData(channel, payload);
                case REQUEST_END -> new Frame.RequestEnd(channel);
                case RESPONSE_START -> new Frame.ResponseStart(channel, p.getShort() & 0xffff, getHeaders(p));
                case RESPONSE_DATA -> new Frame.ResponseData(channel, payload);
                case RESPONSE_END -> new Frame.ResponseEnd(channel);
                case CANCEL -> new Frame.Cancel(channel, CancelReason.fromCode(p.get() & 0xff));
                case ERROR -> {
                    ErrorCode code = ErrorCode.fromCode(p.get() & 0xff);
                    int msgLen = p.getShort() & 0xffff;
                    byte[] msg = new byte[msgLen];
                    p.get(msg);
                    yield new Frame.Error(channel, code, new String(msg, StandardCharsets.UTF_8));
                }
                case PING -> new Frame.Ping(p.getLong());
                case PONG -> new Frame.Pong(p.getLong());
            };
        } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
            throw new ProtocolException(type + " payload is malformed");
        }
    }

    // ---------------------------------------------------------------- helpers

    static boolean isStart(FrameType type) {
        return type == FrameType.REQUEST_START || type == FrameType.RESPONSE_START;
    }

    private static void putHeaders(ByteBuffer b, Headers headers) {
        if (headers.size() > 0xffff) {
            throw new ProtocolException("too many headers");
        }
        b.putShort((short) headers.size());
        headers.forEach((name, value) -> {
            putShortString16(b, name);
            putShortString16(b, value);
        });
    }

    private static Headers getHeaders(ByteBuffer p) {
        int count = p.getShort() & 0xffff;
        Headers headers = new Headers();
        for (int i = 0; i < count; i++) {
            headers.add(getShortString16(p), getShortString16(p));
        }
        return headers;
    }

    private static void putShortString8(ByteBuffer b, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) {
            throw new ProtocolException("string too long: " + s);
        }
        b.put((byte) bytes.length);
        b.put(bytes);
    }

    private static void putShortString16(ByteBuffer b, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xffff) {
            throw new ProtocolException("string too long");
        }
        b.putShort((short) bytes.length);
        b.put(bytes);
    }

    private static String getShortString8(ByteBuffer p) {
        byte[] bytes = new byte[p.get() & 0xff];
        p.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String getShortString16(ByteBuffer p) {
        byte[] bytes = new byte[p.getShort() & 0xffff];
        p.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    private static byte[] trim(ByteBuffer b) {
        byte[] out = new byte[b.position()];
        b.flip();
        b.get(out);
        return out;
    }
}
