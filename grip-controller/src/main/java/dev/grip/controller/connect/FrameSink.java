package dev.grip.controller.connect;

/**
 * Where an {@link AgentConnection} writes frames. Abstracted so the connection
 * logic can be tested without a real WebSocket.
 */
public interface FrameSink {

    /** Send one already-encoded frame line (no trailing newline needed). */
    void send(String line);

    /** Close the underlying transport. */
    void close();
}
