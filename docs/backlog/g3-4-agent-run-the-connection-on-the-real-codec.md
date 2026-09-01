# G3.4 — Agent: run the connection on the real codec

**Stage:** Stage 3 — GRIP framing protocol

## Motivation

Replace the Stage 2 placeholder on the Agent side.

## Scope

- Read/write `Frame`s via `FrameCodec`.
- REGISTER/heartbeat/request/response all framed.
- A malformed frame from the Controller drops the connection → reconnect.

## Implementation notes

- Mirror G3.3.
- Reader on its own virtual thread; heartbeat scheduler unchanged.

## Acceptance criteria

- Stage 2 e2e tests pass over frames from both ends.
- Malformed input from the Controller triggers a clean reconnect.

## Tests required

- Agent test: malformed frame → reconnect; valid frames → normal flow.

## Dependencies

- G3.2
