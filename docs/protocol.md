# GRIP Protocol

This page is the running record of what the GRIP wire protocol **is** and what
has been **deliberately left open**. It is intentionally incomplete: the wire
format is designed in [Stage 3](roadmap.md), not up front.

## Decided

### Shape

- A GRIP connection is a **single WebSocket over TLS** (`wss://`), opened by
  the Agent to the Controller.
- The connection carries a sequence of **frames**.
- The connection multiplexes many **channels**. One channel = one in-flight
  external HTTP request/response.
- There is **no** requirement of one connection per external request.

!!! note "Why WebSocket and not a plain HTTP stream"
    The original intent was one long-lived HTTP request with both bodies held
    open. That is not implementable with the JDK HTTP client (`java.net.http`),
    which always finishes sending a request before it surfaces the response —
    there is no full-duplex HTTP mode. WebSocket is genuinely full-duplex over
    one connection, is still Agent-initiated and plain `wss://`, and passes
    the corporate proxies that allow Slack/Teams. Everything else about the
    protocol — frames, channels, multiplexing, cancellation — is unchanged.

### Channels

- Channel IDs are allocated by the **Controller**.
- A channel ID is unique for the lifetime of its connection.
- After a channel closes, its ID may be reclaimed.
- Every data-plane frame names its channel. Connection-control frames
  (`PING`/`PONG`) do not. (`dev.grip.protocol.FrameType`, `ChannelId`.)

### Frame kinds

| Frame | Scope | Meaning |
|---|---|---|
| `REQUEST_START` | channel | Method, path, headers for a proxied request |
| `REQUEST_DATA` | channel | A chunk of the request body |
| `REQUEST_END` | channel | Request body complete |
| `RESPONSE_START` | channel | Status and headers |
| `RESPONSE_DATA` | channel | A chunk of the response body |
| `RESPONSE_END` | channel | Response complete; channel finished |
| `CANCEL` | channel | Abandon this channel (client gone, service failed, shutdown) |
| `ERROR` | channel | Channel ends abnormally; carries a code |
| `PING` / `PONG` | connection | Heartbeat / liveness |

Connection-level control for **REGISTER** (Agent identity) and heartbeat
details are part of the same frame model; their binary encoding is fixed in
Stage 3.

**Stage 1 provisional framing.** Until the binary codec lands, connection
lifecycle uses a line-oriented placeholder (`dev.grip.protocol.wire.ControlFrame`),
one frame per WebSocket text message:

| Frame | Direction | Meaning |
|---|---|---|
| `REGISTER <agentId> <version>` | Agent → Controller | first frame; claims an id |
| `REGISTER_OK` | Controller → Agent | admitted |
| `REGISTER_REJECTED <reason>` | Controller → Agent | `DUPLICATE_AGENT_ID` \| `UNSUPPORTED_VERSION` \| `MALFORMED` \| `RESERVED_AGENT_ID`, then close |
| `PING` / `PONG` | either | heartbeat |
| `BYE` | either | graceful close |

`agentId` must match `[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?` and must not be a
reserved name (`www`, `api`, `controller`, `health`, `admin`, `grip`).

### Streaming

- Neither side buffers a whole request or response.
- Bodies flow as `*_DATA` frames between a `*_START` and its `*_END`.
- Bodies are **not** base64-encoded. Frame payloads are bytes.

### Transport

- A single WebSocket over TLS, opened by the Agent to
  `wss://<controller>/grip/connect`.
- The GRIP framing is carried in WebSocket messages and does not depend on any
  WebSocket-specific semantics beyond "ordered, reliable, bidirectional
  messages" — it could move to another such carrier without changing the
  frames.
- The Agent opens exactly one WebSocket. WebSocket's own ping/pong is separate
  from GRIP's `PING`/`PONG` frames (which also drive Agent-liveness tracking).

### TLS

- Mandatory. Full certificate-chain validation and hostname verification on the
  Agent side. No insecure mode exists.

## Deferred until implementation

These are **open on purpose**. Picking them now would be guessing.

- **Binary vs. text framing.** Leaning binary (length-prefixed). To be decided
  in Stage 3 with a written spec.
- **Frame header layout** — type tag, channel ID width, length field width,
  flags.
- **Header encoding** — how HTTP headers are represented inside `REQUEST_START`
  / `RESPONSE_START` (name/value list, HPACK-like, plain).
- **Flow control / backpressure** — per-channel windows vs. relying on the
  underlying stream's flow control. Addressed in Stage 5.
- **Channel ID allocation** — monotonic vs. free-list reuse, and the exact
  width (`ChannelId` currently wraps a `long` as a placeholder).
- **REGISTER exchange** — the Stage 1 line form above is provisional; the
  binary encoding, any richer handshake (capabilities, auth), and whether it
  moves off a plain text message are for Stage 3.
- **Error codes** — the enumerated set for `ERROR` (the `REGISTER_REJECTED`
  reasons are settled).
- **Settings/negotiation** — max frame size, max concurrent channels, heartbeat
  interval: fixed constants for now (`GripProtocol`), negotiated later if
  needed.
- **Trailers** — whether HTTP trailers are forwarded.
- **Multiple services per Agent** — the model allows it; v1 exposes exactly
  one. Out of scope until after Stage 6.

## Provisional limits

From `dev.grip.protocol.GripProtocol`, all subject to change in Stage 5:

| Constant | Value |
|---|---|
| `VERSION` | `0` |
| `AGENT_CONNECT_PATH` | `/grip/connect` |
| `MAX_FRAME_PAYLOAD_BYTES` | 64 KiB |
| `MAX_HEADER_BYTES` | 32 KiB |
| `MAX_CHANNELS_PER_CONNECTION` | 1024 |
