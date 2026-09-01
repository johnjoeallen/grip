# G7.2 — Internal service failure handling

**Stage:** Stage 7 — Failure handling

## Motivation

The internal service can be down, slow, or drop mid-response.

## Scope

- Connect failure → `ERROR`/502. Read timeout → `ERROR`/504. Mid-response disconnect → `ERROR`, truncate the external response cleanly (or reset the stream).
- Configurable internal connect/read timeouts on the Agent.

## Implementation notes

- Distinguish 'never started' (safe to 502) from 'partially sent' (cannot change status).

## Acceptance criteria

- Each internal failure mode maps to the defined external outcome.
- No hung channels afterwards.

## Tests required

- Agent + e2e tests for connect-refused, timeout, and mid-stream drop.

## Dependencies

- G3.5
