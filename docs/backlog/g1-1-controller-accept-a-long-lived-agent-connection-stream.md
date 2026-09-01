# G1.1 — Controller: accept a long-lived Agent connection stream

**Stage:** Stage 1 — Basic Agent connection

## Motivation

The Controller needs an endpoint an Agent can hold open indefinitely.

## Scope

- Add an endpoint at `GripProtocol.AGENT_CONNECT_PATH` (`/grip/connect`) that accepts a streaming request and keeps the response open.
- Hand the open byte streams to a connection handler object; do not parse GRIP frames yet (that is Stage 3) — a placeholder line protocol is fine.
- One connection object per open stream; log connect/disconnect.

## Implementation notes

- Use Spring MVC with `StreamingResponseBody` / async request handling on virtual threads, or a raw servlet — whichever keeps both directions streamable.
- No Agent identity or registry yet (G1.3).

## Acceptance criteria

- An Agent-side test client can open the stream, hold it, send bytes, and receive bytes.
- Closing either side is detected and logged within the heartbeat window.

## Tests required

- Controller test: open the endpoint, exchange bytes, assert both directions stream without buffering to EOF.

## Dependencies

- G0.1
