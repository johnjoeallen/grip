package dev.grip.agent.connect;

import java.net.URI;
import java.time.Duration;

/**
 * Everything an {@link AgentConnection} needs, so its constructor stays small
 * as the connection grows.
 *
 * @param controllerBase   {@code https://} base URL of the Controller
 * @param targetUri         the one internal service this Agent forwards to
 * @param agentId           the name to register as
 * @param handshakeTimeout  cap on the connect + REGISTER round trip
 * @param heartbeatInterval how often to send {@code PING}
 * @param heartbeatTimeout  no inbound frame for this long ⇒ drop the connection
 */
public record ConnectionConfig(
        URI controllerBase,
        URI targetUri,
        String agentId,
        Duration handshakeTimeout,
        Duration heartbeatInterval,
        Duration heartbeatTimeout) {
}
