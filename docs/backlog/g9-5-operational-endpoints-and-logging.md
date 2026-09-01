# G9.5 — Operational endpoints and logging

**Stage:** Stage 9 — Production packaging

## Motivation

Running services need to be observable.

## Scope

- Controller: health, readiness, and a small status endpoint (connected Agents, active channels) — bound appropriately, not on the public vhost.
- Agent: health/readiness incl. connection state and last-heartbeat.
- Structured request logging (agentId, channel, method, path, status, bytes, duration) with no sensitive values.
- Metrics via Micrometer (counts, gauges, timers) — no new infrastructure, just the registry beans.

## Implementation notes

- Keep the status endpoint read-only and access-controlled by network placement.
- Document every field.

## Acceptance criteria

- Health/readiness reflect real state (e.g. Agent readiness false while disconnected).
- Logs and metrics cover a request's life.

## Tests required

- Endpoint tests; a log-assertion test for the request log shape.

## Dependencies

- G9.1
