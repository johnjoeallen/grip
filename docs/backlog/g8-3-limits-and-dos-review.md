# G8.3 — Limits and DoS review

**Stage:** Stage 8 — Security hardening

## Motivation

Confirm the service degrades, not collapses, under abuse.

## Scope

- Review every limit for a sane production default: max connections, channels/connection, header bytes, body bytes (optional cap), queued bytes, request rate per Agent connection attempt.
- Add connection-accept backpressure on the Controller.
- Reconnect-storm protection: Controller-side rejection/backoff for an Agent id that reconnects too fast.

## Implementation notes

- Everything configurable; defaults documented.
- Prefer shedding load (503) over unbounded queueing.

## Acceptance criteria

- A load test at 2–3× expected concurrency stays responsive for healthy traffic.
- A reconnect storm from one bad Agent doesn't affect others.

## Tests required

- Load/abuse test suite (opt-in in CI).

## Dependencies

- G5.4
- G7.4
