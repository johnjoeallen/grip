# G6.1 — Configurable wildcard-domain routing

**Stage:** Stage 6 — Host routing

## Motivation

Routing should be driven entirely by `grip.base-domain`.

## Scope

- Extract routing into a `HostRouter` component: host → agentId.
- Support an optional list of additional accepted base domains.
- Case-insensitive, port-stripping, trailing-dot tolerant.

## Implementation notes

- No `example.com` anywhere in code or defaults beyond the sample `application.yml`.
- Reject a host whose label count/shape doesn't match `<agent>.<base-domain>`.

## Acceptance criteria

- `alpha.<base-domain>` → `alpha` across a table of tricky inputs.
- Changing `base-domain` in config changes routing with no code change.

## Tests required

- `HostRouterTest` with a wide input table.

## Dependencies

- G4.4
