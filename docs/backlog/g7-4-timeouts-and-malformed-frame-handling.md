# G7.4 — Timeouts and malformed-frame handling

**Stage:** Stage 7 — Failure handling

## Motivation

Bound every wait; reject every bad input.

## Scope

- Timeouts: Agent register, idle connection (heartbeat), internal connect/read, whole-request ceiling (configurable, optional).
- Malformed frame → connection close with a protocol `ERROR` logged; never a partial-state hang.
- Oversized REGISTER / header block → reject before allocating.

## Implementation notes

- Centralise timeout config under `grip.*`.
- One place that maps codec exceptions → connection teardown.

## Acceptance criteria

- Each timeout fires and is logged with context.
- A corpus of malformed frames all produce clean closes.

## Tests required

- Timeout tests per case; malformed-frame corpus test.

## Dependencies

- G3.3
- G3.4
