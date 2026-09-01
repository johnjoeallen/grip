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

**Stage 1 provisional framing.** Superseded by [the frame format](#the-frame-format-v0)
once Stages 3.3/3.4 land. Historically, connection lifecycle used a
line-oriented placeholder (`dev.grip.protocol.wire.ControlFrame`), one frame
per WebSocket text message:

| Frame | Direction | Meaning |
|---|---|---|
| `REGISTER <agentId> <version>` | Agent → Controller | first frame; claims an id |
| `REGISTER_OK` | Controller → Agent | admitted |
| `REGISTER_REJECTED <reason>` | Controller → Agent | `DUPLICATE_AGENT_ID` \| `UNSUPPORTED_VERSION` \| `MALFORMED` \| `RESERVED_AGENT_ID`, then close |
| `PING` / `PONG` | either | heartbeat |
| `BYE` | either | graceful close |

`agentId` must match `[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?` and must not be a
reserved name (`www`, `api`, `controller`, `health`, `admin`, `grip`).

**Stage 2 provisional proxy framing.** Also superseded by
[the frame format](#the-frame-format-v0). The first end-to-end request slice
used `dev.grip.protocol.wire.ProxyMessage` + `ProxyCodec`: one JSON object per
WebSocket text message, telling proxy messages from control frames by a
leading `{`.

| Message | Direction | Shape |
|---|---|---|
| `{"t":"req","ch":…,"method":…,"target":"/path?q",…,"body":"<base64>"}` | Controller → Agent | whole request |
| `{"t":"resp","ch":…,"status":…,"headers":…,"body":"<base64>"}` | Agent → Controller | whole response |
| `{"t":"fail","ch":…,"code":"BAD_GATEWAY","message":…}` | Agent → Controller | internal call failed |

This is a throwaway: **one in-flight request per Agent** (a second gets
`503`), bodies buffered and base64'd. Stage 3 replaces it with the streamed
binary frames and bodies stop being base64; Stage 4 lifts the single-request
limit.

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

## The frame format (v0)

Decided in Stage 3. One GRIP frame per WebSocket **binary** message. The frame
is also self-delimiting (it carries its own length) so it does not depend on
WebSocket message boundaries — the same bytes are meaningful over any ordered,
reliable byte stream.

### Frame layout

```
 0        1        2                                10       14
 +--------+--------+--------------------------------+--------+· · · · · ·+
 | type   | flags  |         channel  (u64)         | length |  payload  |
 | u8     | u8     |         big-endian             | u32 BE |  = length |
 +--------+--------+--------------------------------+--------+· · · · · ·+
```

- **type** — the frame kind (table below).
- **flags** — reserved, always `0` in v0. A reader must reject a non-zero
  flags byte it does not understand.
- **channel** — the channel this frame belongs to; `0` for connection-scoped
  frames (`REGISTER*`, `PING`, `PONG`).
- **length** — payload byte count. Must be ≤ `MAX_FRAME_PAYLOAD_BYTES`
  (for a `*_DATA` payload) or `MAX_HEADER_BYTES` (for a `*_START` payload).

### Frame types

| type | name | scope | payload |
|---|---|---|---|
| `0x01` | `REGISTER` | conn | `u16` protocol version, `u8` idLen, id (UTF-8) |
| `0x02` | `REGISTER_OK` | conn | empty |
| `0x03` | `REGISTER_REJECTED` | conn | `u8` reason code |
| `0x10` | `REQUEST_START` | channel | method + target + headers (below) |
| `0x11` | `REQUEST_DATA` | channel | body bytes |
| `0x12` | `REQUEST_END` | channel | empty |
| `0x20` | `RESPONSE_START` | channel | status + headers (below) |
| `0x21` | `RESPONSE_DATA` | channel | body bytes |
| `0x22` | `RESPONSE_END` | channel | empty |
| `0x30` | `CANCEL` | channel | `u8` cancel reason |
| `0x31` | `ERROR` | channel | `u8` error code, `u16` msgLen, message (UTF-8) |
| `0x40` | `PING` | conn | `u64` nonce |
| `0x41` | `PONG` | conn | `u64` nonce (echoed) |

### Header block (`REQUEST_START` / `RESPONSE_START`)

```
REQUEST_START :  u8 methodLen | method | u16 targetLen | target | headers
RESPONSE_START:  u16 status | headers
headers       :  u16 count | count × ( u16 nameLen | name | u16 valueLen | value )
```

- `target` is the request path plus query (`/books?author=x`).
- One entry per header value; a repeated header is repeated entries, in order.
- Names and values are UTF-8. The whole `*_START` payload is bounded by
  `MAX_HEADER_BYTES`.
- **Hop-by-hop** headers (`Connection` and what it names, `Keep-Alive`, `TE`,
  `Trailer`, `Transfer-Encoding`, `Upgrade`, `Proxy-*`) and `Content-Length`
  are not carried; each side derives framing from the `*_DATA`/`*_END` frames.

### Reason / error codes

`REGISTER_REJECTED` reason (`u8`): `0` MALFORMED, `1` UNSUPPORTED_VERSION,
`2` DUPLICATE_AGENT_ID, `3` RESERVED_AGENT_ID.

`CANCEL` reason (`u8`): `0` UNSPECIFIED, `1` CLIENT_GONE, `2` SERVICE_FAILED,
`3` SHUTDOWN.

`ERROR` code (`u8`): `0` INTERNAL, `1` PROTOCOL, `2` BAD_GATEWAY,
`3` GATEWAY_TIMEOUT, `4` TOO_LARGE.

### Channel rules

- The **Controller** allocates channel IDs, monotonically increasing per
  connection, starting above `0`.
- A channel is live from its first `REQUEST_START` until `RESPONSE_END`,
  `CANCEL`, or `ERROR`. After that the id is retired and not reused within the
  connection (reuse is a possible later optimisation).
- Receiving a channel-scoped frame for an unknown or retired channel is
  ignored (it is a losing race with `CANCEL`/`END`), except `REQUEST_START`
  for an id at/below the highest allocated, which is a `PROTOCOL` error.

### Malformed input

A frame that is truncated, over a size limit, has an unknown `type` or a
non-zero `flags`, or whose payload does not match its declared shape, is a
**connection-fatal** protocol error: the reader closes the connection (the
Controller drops the Agent; the Agent reconnects).

## Still deferred

- **Flow control / backpressure** — per-channel credit windows vs. relying on
  the carrier. Stage 5.
- **Settings / negotiation** — max frame size, max channels, heartbeat
  interval are fixed constants (`GripProtocol`) for now.
- **Trailers** — HTTP trailers are not forwarded yet.
- **Richer REGISTER** — capabilities, auth. The frame has a version field to
  hang a handshake off later.
- **Multiple services per Agent** — the model allows it; v1 exposes one.
- **Channel-id reuse** within a connection.

## Provisional limits

From `dev.grip.protocol.GripProtocol`, all subject to change in Stage 5:

| Constant | Value |
|---|---|
| `VERSION` | `0` |
| `AGENT_CONNECT_PATH` | `/grip/connect` |
| `MAX_FRAME_PAYLOAD_BYTES` | 64 KiB |
| `MAX_HEADER_BYTES` | 32 KiB |
| `MAX_CHANNELS_PER_CONNECTION` | 1024 |
