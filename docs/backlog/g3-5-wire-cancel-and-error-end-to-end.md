# G3.5 — Wire CANCEL and ERROR end to end

**Stage:** Stage 3 — GRIP framing protocol

## Motivation

Channels need explicit abnormal-termination semantics before multiplexing.

## Scope

- Controller sends `CANCEL` for a channel when it decides to abandon it.
- Agent sends `ERROR` (with code) when the internal request fails, and handles inbound `CANCEL` by aborting the internal request.
- Both sides free channel state on CANCEL/ERROR/RESPONSE_END.

## Implementation notes

- Define the error-code enum in `grip-protocol` (from G3.1).
- Idempotent: a second CANCEL for a freed channel is ignored.

## Acceptance criteria

- An Agent `ERROR` becomes a 502 to the external client.
- A Controller `CANCEL` stops the Agent's internal request.

## Tests required

- e2e: internal 500 → client 502 with the error surfaced in logs.
- e2e: Controller cancels mid-flight → Agent aborts (observable via the `/slow` endpoint).

## Dependencies

- G3.3
- G3.4
