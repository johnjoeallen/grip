# G7.1 — External client disconnect cancels the channel

**Stage:** Stage 7 — Failure handling

## Motivation

Abandoned requests must not keep working.

## Scope

- Detect external client disconnect (broken output stream / async error).
- Send `CANCEL` for that channel; free Controller state.
- Agent aborts the internal request on `CANCEL`.

## Implementation notes

- Debounce: don't CANCEL a channel that already completed.
- Covered partly by G3.5; this is the client-triggered path + tests.

## Acceptance criteria

- Killing the client during `/slow` causes the Agent's internal request to abort within a short window.

## Tests required

- e2e: client disconnect mid-`/slow`; assert Agent-side abort (probe via a counter on the internal service).

## Dependencies

- G4.4
