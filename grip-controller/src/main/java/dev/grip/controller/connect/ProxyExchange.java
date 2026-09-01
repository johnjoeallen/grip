package dev.grip.controller.connect;

import dev.grip.protocol.wire.CancelReason;
import dev.grip.protocol.wire.ErrorCode;
import dev.grip.protocol.wire.Frame;
import dev.grip.protocol.wire.Headers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * One in-flight proxied request on a connection. Consumes the response frames
 * the Agent sends back and completes {@link #result()} once the channel ends.
 *
 * <p>Stage 3 still buffers the response body; Stage 5 makes it stream.
 */
public final class ProxyExchange {

    public sealed interface Result {
        record Ok(int status, Headers headers, byte[] body) implements Result { }
        record Failed(ErrorCode code, String message) implements Result { }
        record Cancelled(CancelReason reason) implements Result { }
    }

    private final long channel;
    private final CompletableFuture<Result> result = new CompletableFuture<>();
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private int status;
    private Headers headers = Headers.of();

    ProxyExchange(long channel) {
        this.channel = channel;
    }

    public long channel() {
        return channel;
    }

    public CompletableFuture<Result> result() {
        return result;
    }

    /** Feed a channel-scoped frame received from the Agent. */
    void accept(Frame frame) {
        switch (frame) {
            case Frame.ResponseStart s -> {
                status = s.status();
                headers = s.headers();
            }
            case Frame.ResponseData d -> body.writeBytes(d.data());
            case Frame.ResponseEnd ignored ->
                    result.complete(new Result.Ok(status, headers, body.toByteArray()));
            case Frame.Error e -> result.complete(new Result.Failed(e.code(), e.message()));
            case Frame.Cancel c -> result.complete(new Result.Cancelled(c.reason()));
            default -> { }
        }
    }

    void failClosed(String reason) {
        if (!result.isDone()) {
            result.completeExceptionally(new IOException("connection closed: " + reason));
        }
    }

    /** The Controller decided to cancel — unblock the waiter without waiting for the Agent. */
    void cancelledLocally(CancelReason reason) {
        if (!result.isDone()) {
            result.complete(new Result.Cancelled(reason));
        }
    }
}
