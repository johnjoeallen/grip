# G5.3 — Per-channel flow control and bounded queues

**Stage:** Stage 5 — Streaming and backpressure

## Motivation

A slow reader on one side must slow the producer on the other, not fill memory.

## Scope

- Bounded queue of pending bytes/frames per channel in the connection writer.
- When a channel's queue is full, pause reading from that channel's source (external body read, or internal response read).
- Resume when it drains. Choose credit-based windows or simple high/low-watermark pausing — document the choice in `protocol.md`.

## Implementation notes

- Must not deadlock: pausing one channel cannot block the shared reader for others.
- Interacts with G4.3 fairness.

## Acceptance criteria

- A slow external client causes the internal read to pause; heap stays bounded; other channels keep flowing.

## Tests required

- Test: slow-consuming client + fast internal producer; assert bounded queued bytes and continued progress on a second channel.

## Dependencies

- G5.1
- G5.2
