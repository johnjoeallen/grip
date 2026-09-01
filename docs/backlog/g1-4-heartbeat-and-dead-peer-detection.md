# G1.4 — Heartbeat and dead-peer detection

**Stage:** Stage 1 — Basic Agent connection

## Motivation

TCP alone is too slow to notice a silently dead peer.

## Scope

- Agent sends `PING` every `grip.reconnect.heartbeat-interval` on an idle connection; Controller replies `PONG`.
- Controller marks an Agent dead if no frame arrives within `grip.connect.agent-timeout` and closes the connection.
- Agent treats a missing `PONG` within a timeout as a dead connection and closes it (triggering reconnect, G1.5).

## Implementation notes

- Reuse the `FrameType.PING`/`PONG` concepts.
- Scheduled work on a single-thread scheduler; keep it simple.

## Acceptance criteria

- Killing the Controller's socket without a FIN is detected within `agent-timeout`.
- A healthy idle connection stays up indefinitely.

## Tests required

- Controller test: simulate a stalled Agent, assert it is reaped after the timeout.
- Agent test: simulate missing PONGs, assert the connection is dropped.

## Dependencies

- G1.3
