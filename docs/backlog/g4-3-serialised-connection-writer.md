# G4.3 — Serialised connection writer

**Stage:** Stage 4 — Multiplexing

## Motivation

Frames from many channels share one connection and must not interleave mid-frame.

## Scope

- A single writer per connection: a queue that channels submit whole frames to, drained by one writer thread/loop.
- Fairness across channels (round-robin or FIFO with per-channel limits).
- Backpressure hook for Stage 5 (bounded queue).

## Implementation notes

- Whole frames are the unit of atomicity; a large body is already split into `*_DATA` frames by the codec/limits.
- Applies on both Controller and Agent.

## Acceptance criteria

- Under load from many channels, no frame is ever split by another frame's bytes.
- One slow channel cannot starve others indefinitely.

## Tests required

- Concurrency test: N producers, one connection, decode the output and assert every frame is intact and attributable.

## Dependencies

- G4.1
- G4.2
