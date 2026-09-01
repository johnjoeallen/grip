# G9.2 — systemd units and Docker images

**Stage:** Stage 9 — Production packaging

## Motivation

Two standard deployment shapes.

## Scope

- `examples/systemd/grip-controller.service` and `grip-agent.service` (hardened: `DynamicUser`, `ProtectSystem`, restart policy).
- Dockerfiles for both (distroless or `-jammy` JRE base), plus a `compose.yaml` for the Agent-side (Agent + sample service).
- Publish images from CI on tags (optional, behind a flag).

## Implementation notes

- Non-root in the containers.
- Document env-var configuration for the container case.

## Acceptance criteria

- A container Agent connects to a container Controller and proxies.
- The systemd units start/stop/restart cleanly.

## Tests required

- CI builds the images; a compose-based e2e smoke test (opt-in).

## Dependencies

- G9.1
