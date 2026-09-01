# G6.3 — Routing end-to-end tests

**Stage:** Stage 6 — Host routing

## Motivation

Confirm routing works through the whole chain, not just in isolation.

## Scope

- e2e: two Agents (`alpha`, `beta`) → two internal services; assert each sub-domain hits the right one.
- e2e: disconnect `beta`, assert its sub-domain returns 503 while `alpha` still works.

## Implementation notes

- Reuse the G2.4 fixture, extended to N Agents.

## Acceptance criteria

- Multi-Agent routing verified end to end, including the disconnect case.

## Tests required

- `RoutingE2ETest`.

## Dependencies

- G6.2
