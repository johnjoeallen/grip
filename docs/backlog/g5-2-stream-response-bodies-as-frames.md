# G5.2 — Stream response bodies as frames

**Stage:** Stage 5 — Streaming and backpressure

## Motivation

Large downloads and slow producers must not be buffered.

## Scope

- Agent reads the internal response body incrementally into `RESPONSE_DATA` frames, then `RESPONSE_END`.
- Controller writes them to the external response as they arrive and flushes.
- Support an internal service that streams slowly / indefinitely (SSE-like).

## Implementation notes

- Bounded buffering per channel.
- Flush semantics so the client sees data promptly.

## Acceptance criteria

- A large / slow / unbounded internal response reaches the client incrementally with bounded heap on both sides.

## Tests required

- e2e: `/slow`-style trickle endpoint; assert the client receives bytes before the response completes.

## Dependencies

- G4.4
