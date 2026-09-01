# G2.1 — Controller: resolve external Host to a connected Agent

**Stage:** Stage 2 — Single proxied request

## Motivation

Routing is the Controller's first proxy responsibility.

## Scope

- Parse the external request `Host`; strip port; lower-case.
- Expect `<agentId>.<grip.base-domain>`; extract `agentId`.
- Look up `AgentRegistry`. If missing → 404; if the host isn't under `base-domain` → 404; if the Agent id is known but disconnected → 503.
- Return a small JSON/plain error body for each case.

## Implementation notes

- `base-domain` comes from `GripControllerProperties`; never hard-coded.
- Full wildcard routing polish is Stage 6 — here just the happy path plus the three error cases.

## Acceptance criteria

- `alpha.<base-domain>` with `alpha` connected resolves to its connection.
- Unknown/disconnected/off-domain hosts return 404/503/404 respectively.

## Tests required

- Controller unit test over a table of hosts → expected outcome.

## Dependencies

- G1.3
