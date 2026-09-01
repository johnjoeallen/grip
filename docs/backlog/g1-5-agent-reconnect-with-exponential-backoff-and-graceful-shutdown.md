# G1.5 — Agent reconnect with exponential backoff and graceful shutdown

**Stage:** Stage 1 — Basic Agent connection

## Motivation

A dropped connection must recover on its own without hammering the Controller.

## Scope

- On any disconnect, the Agent retries: `initial-backoff`, doubling to `max-backoff`, with small jitter.
- Reset the backoff after a connection stays up for a stable period.
- On SIGTERM, the Agent sends a clean disconnect and stops retrying.

## Implementation notes

- A single reconnect loop, not one per failure.
- Log each attempt at DEBUG, give up only on fatal config errors (e.g. bad URL), not on transient failures.

## Acceptance criteria

- Bring the Controller down and up; the Agent reconnects and re-registers with increasing then reset backoff.
- `kill -TERM` on the Agent exits within a couple of seconds with a disconnect logged.

## Tests required

- Agent test: fake transport that fails N times then succeeds; assert delays follow the backoff schedule and registration eventually succeeds.

## Dependencies

- G1.4
