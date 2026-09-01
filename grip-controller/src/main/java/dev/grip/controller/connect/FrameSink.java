package dev.grip.controller.connect;

import dev.grip.protocol.wire.Frame;

/**
 * Where an {@link AgentConnection} writes frames. Abstracted so the connection
 * logic can be tested without a real WebSocket.
 */
public interface FrameSink {

    void send(Frame frame);

    /** Close the underlying transport. */
    void close();
}
