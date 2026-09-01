# G4.4 — Multiplexing correctness test: no cross-channel leakage

**Stage:** Stage 4 — Multiplexing

## Motivation

The core guarantee of GRIP must be proven, including out-of-order completion.

## Scope

- e2e test: fire 2+ simultaneous requests to one Agent, with the internal service returning distinct, identifiable bodies and varying delays so responses complete out of order.
- Assert each external client receives exactly its own response, byte-for-byte.
- Repeat under a stress loop (e.g. 50 concurrent × 20 iterations).

## Implementation notes

- Use `/echo` with a per-request marker and `/slow?ms=` to force reordering.
- Run in CI but keep the stress count modest.

## Acceptance criteria

- Zero cross-channel contamination across the stress run.
- Responses observed completing in a different order than requested.

## Tests required

- `MultiplexingE2ETest` in `grip-integration-tests`.

## Dependencies

- G4.3
