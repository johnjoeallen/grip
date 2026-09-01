# G1.6 — Duplicate Agent ID handling

**Stage:** Stage 1 — Basic Agent connection

## Motivation

Two Agents claiming the same id must resolve deterministically.

## Scope

- Decide and implement the policy: reject the second REGISTER while the first is connected (recommended), returning REJECTED with a reason.
- Ensure a reconnecting Agent whose previous connection is half-open can still take over once the stale one is reaped.

## Implementation notes

- Document the chosen policy in `docs/protocol.md` under REGISTER.
- Consider a short grace window so a fast reconnect after a network blip isn't rejected forever.

## Acceptance criteria

- Second concurrent REGISTER for `alpha` is rejected with a clear reason.
- After the first connection is reaped, `alpha` can register again.

## Tests required

- Controller test: connect `alpha`, attempt a second `alpha`, assert rejection; reap the first, assert the second now succeeds.

## Dependencies

- G1.3
