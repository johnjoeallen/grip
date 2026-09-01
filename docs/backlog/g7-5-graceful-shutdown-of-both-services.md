# G7.5 — Graceful shutdown of both services

**Stage:** Stage 7 — Failure handling

## Motivation

SIGTERM should drain, not sever.

## Scope

- Controller: stop accepting new external requests and new Agent connections; let in-flight channels finish within a grace period, then `CANCEL` the rest and close.
- Agent: stop accepting new channels; finish or cancel in-flight; send a clean disconnect; exit.
- Grace period configurable.

## Implementation notes

- Hook Spring's lifecycle / `SmartLifecycle` or a shutdown hook.
- Return non-zero only on a genuinely dirty exit.

## Acceptance criteria

- `kill -TERM` during load: in-flight requests within the grace window complete; the rest end cleanly; process exits 0.

## Tests required

- e2e: shutdown under load, assert the drain behaviour and exit code.

## Dependencies

- G7.3
