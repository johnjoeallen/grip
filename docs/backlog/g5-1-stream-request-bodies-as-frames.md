# G5.1 — Stream request bodies as frames

**Stage:** Stage 5 — Streaming and backpressure

## Motivation

Large uploads must not be buffered in memory.

## Scope

- Controller reads the external request body incrementally and emits `REQUEST_DATA` frames up to `MAX_FRAME_PAYLOAD_BYTES`, then `REQUEST_END`.
- Agent writes received `REQUEST_DATA` straight to the internal request body as it arrives.
- Remove any full-body buffering from Stage 2.

## Implementation notes

- Bounded read-ahead per channel.
- Handle `Content-Length` and chunked transfer on both ends.

## Acceptance criteria

- A multi-hundred-MB upload proxies with bounded Controller and Agent heap.
- Interrupted mid-upload → CANCEL/ERROR, no leak.

## Tests required

- e2e: stream a large generated body to `/echo`, assert equality and cap heap via a small `-Xmx` in the test JVM.

## Dependencies

- G4.4
