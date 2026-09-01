# G5.5 — Memory-safety test under adversarial clients

**Stage:** Stage 5 — Streaming and backpressure

## Motivation

Prove the slow/greedy client cannot cause unbounded growth.

## Scope

- A test that runs many slow-loris-style clients plus large bodies against a heap-capped Controller, for a sustained period.
- Assert steady-state heap and no OOM.

## Implementation notes

- Keep runtime CI-friendly (a minute or two).
- Document the observed ceilings.

## Acceptance criteria

- The scenario runs green with a tight `-Xmx`.

## Tests required

- `MemorySafetyE2ETest` (may be tagged slow / opt-in in CI).

## Dependencies

- G5.4
