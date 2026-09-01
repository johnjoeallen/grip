package dev.grip.agent.connect;

import dev.grip.protocol.wire.ErrorCode;
import dev.grip.protocol.wire.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One proxied request on the Agent side: accumulates the request frames, fires
 * the internal call on {@code REQUEST_END}, and streams the reply back as
 * response frames. Stage 3 buffers both bodies.
 */
final class ForwardingChannel {

    private static final Logger log = LoggerFactory.getLogger(ForwardingChannel.class);

    private final long channel;
    private final RequestForwarder forwarder;
    private final Consumer<Frame> out;

    private Frame.RequestStart start;
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private volatile CompletableFuture<HttpResponse<byte[]>> call;
    private volatile boolean cancelled;

    ForwardingChannel(long channel, RequestForwarder forwarder, Consumer<Frame> out) {
        this.channel = channel;
        this.forwarder = forwarder;
        this.out = out;
    }

    void onStart(Frame.RequestStart frame) {
        this.start = frame;
    }

    void onData(Frame.RequestData frame) {
        body.writeBytes(frame.data());
    }

    void onEnd() {
        if (start == null || cancelled) {
            return;
        }
        call = forwarder.send(start, body.toByteArray());
        call.whenComplete((response, error) -> {
            if (cancelled) {
                return;
            }
            if (error != null) {
                log.warn("internal call failed on channel {}: {}", channel, error.toString());
                out.accept(new Frame.Error(channel, classify(error), rootMessage(error)));
                return;
            }
            emit(response);
        });
    }

    void cancel() {
        cancelled = true;
        CompletableFuture<HttpResponse<byte[]>> c = call;
        if (c != null) {
            c.cancel(true);
        }
    }

    boolean isDone() {
        return cancelled || (call != null && call.isDone());
    }

    private void emit(HttpResponse<byte[]> response) {
        out.accept(new Frame.ResponseStart(channel, response.statusCode(),
                RequestForwarder.responseHeaders(response.headers().map())));
        byte[] payload = response.body();
        if (payload.length > 0) {
            out.accept(new Frame.ResponseData(channel, payload));
        }
        out.accept(new Frame.ResponseEnd(channel));
    }

    private static ErrorCode classify(Throwable error) {
        Throwable cur = error;
        while (cur != null) {
            if (cur instanceof java.net.http.HttpTimeoutException) {
                return ErrorCode.GATEWAY_TIMEOUT;
            }
            if (cur instanceof java.net.ConnectException || cur instanceof java.io.IOException) {
                return ErrorCode.BAD_GATEWAY;
            }
            cur = cur.getCause();
        }
        return ErrorCode.BAD_GATEWAY;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + (cur.getMessage() != null ? ": " + cur.getMessage() : "");
    }
}
