# G9.1 — Executable jars and runnable configuration examples

**Stage:** Stage 9 — Production packaging

## Motivation

Operators need artifacts and known-good config.

## Scope

- `spring-boot:repackage` for Controller and Agent; document the jar names and `java -jar` invocation.
- `examples/` with a complete Controller `application.yml` and Agent `application.yml` for a realistic deployment.
- A `docker-compose`-free local demo script that starts a Controller, an Agent, and a sample service.

## Implementation notes

- Keep examples free of real hostnames; use `grip.example.com` clearly marked as a placeholder.

## Acceptance criteria

- `java -jar` starts each service from an example config.
- The demo script brings up a working proxy locally.

## Tests required

- Smoke test that the repackaged jars boot.

## Dependencies

- G8.1
