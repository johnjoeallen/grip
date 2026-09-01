# G1.2 — Agent: outbound TLS connection with strict validation

**Stage:** Stage 1 — Basic Agent connection

## Motivation

The Agent must dial the Controller securely and never with validation off.

## Scope

- On startup, open a connection to `grip.controller-url` + `/grip/connect` using the JDK `HttpClient` (HTTP/2 allowed, must also work over HTTP/1.1).
- Full certificate-chain and hostname verification — no custom `TrustManager`, no `HostnameVerifier` override, no config flag to disable it.
- Reject a non-`https` `controller-url` at startup (already enforced in `GripAgentProperties`); add a startup log line naming the target.

## Implementation notes

- Keep the connection handle in a single `AgentConnection` component.
- Connection lifecycle states: CONNECTING, CONNECTED, DISCONNECTED.

## Acceptance criteria

- Against a TLS test Controller with a trusted cert, the Agent connects.
- Against a self-signed / wrong-hostname cert, the Agent fails to connect with a clear error and does **not** proceed.

## Tests required

- Agent test with a locally-trusted self-signed CA: success path.
- Agent test with an untrusted cert: connection rejected, no insecure fallback.

## Dependencies

- G0.1
