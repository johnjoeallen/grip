package dev.grip.protocol;

/**
 * The kinds of frame that travel over a GRIP connection. These <em>concepts</em>
 * are decided; their binary encoding is not (see {@code docs/protocol.md}).
 *
 * <p>A GRIP connection carries many independent <em>channels</em>, one per
 * in-flight external HTTP request. Every data-plane frame names its channel;
 * connection-control frames ({@link #PING}, {@link #PONG}) do not.
 */
public enum FrameType {

    /** Start of a proxied request: method, path, headers. Channel-scoped. */
    REQUEST_START,

    /** A chunk of the request body. Channel-scoped. */
    REQUEST_DATA,

    /** The request body is complete. Channel-scoped. */
    REQUEST_END,

    /** Start of the response: status and headers. Channel-scoped. */
    RESPONSE_START,

    /** A chunk of the response body. Channel-scoped. */
    RESPONSE_DATA,

    /** The response body is complete; the channel is finished. Channel-scoped. */
    RESPONSE_END,

    /**
     * Either side abandons the channel (external client gone, internal service
     * failed, shutdown). The peer should stop work and release the channel.
     * Channel-scoped.
     */
    CANCEL,

    /**
     * A channel-scoped error that ends the channel abnormally (as opposed to a
     * connection-fatal problem). Carries a code and optional message.
     * Channel-scoped.
     */
    ERROR,

    /** Connection heartbeat / liveness probe. Not channel-scoped. */
    PING,

    /** Reply to a {@link #PING}. Not channel-scoped. */
    PONG;

    /** Whether a frame of this type is bound to a single channel. */
    public boolean isChannelScoped() {
        return this != PING && this != PONG;
    }
}
