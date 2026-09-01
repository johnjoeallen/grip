# G5.4 — Enforce protocol limits

**Stage:** Stage 5 — Streaming and backpressure

## Motivation

Every limit in `GripProtocol` must actually be enforced.

## Scope

- Frame payload size, header block size, concurrent channels per connection, queued bytes per channel, total connections.
- Over-limit → a defined `ERROR` code (channel) or connection close (connection-level), never an OOM.
- Make the limits configurable with the current constants as defaults.

## Implementation notes

- Revisit the provisional numbers in `GripProtocol` and set sensible production defaults.
- Surface limits in `docs/development.md`.

## Acceptance criteria

- Each limit has a test that exceeds it and observes the defined rejection.
- No limit breach can crash a service.

## Tests required

- Limit-enforcement test suite covering every limit.

## Dependencies

- G5.3
