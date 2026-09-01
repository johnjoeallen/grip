# G6.2 — Routing edge cases: unknown, disconnected, invalid, reserved, duplicate

**Stage:** Stage 6 — Host routing

## Motivation

Each failure mode should be explicit and testable.

## Scope

- Unknown agentId → 404. Known but disconnected → 503 with a `Retry-After` hint. Host not under any base domain → 404. Malformed host → 400.
- Reserve names (`www`, `api`, `controller`, health hostnames) — configurable reserved list, never routed to an Agent.
- Duplicate-Agent behaviour from G1.6 reflected here.

## Implementation notes

- Bodies are small `application/problem+json`.
- Log at INFO with agentId + reason.

## Acceptance criteria

- Every case returns its defined status and body.
- A reserved name cannot be claimed by an Agent.

## Tests required

- Controller test table: host → (status, code).

## Dependencies

- G6.1
- G1.6
