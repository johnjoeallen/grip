# G0.1 — CI: build and test the reactor on every push and PR

**Stage:** Stage 0 — Project foundation

## Motivation

The skeleton is committed but nothing enforces that `main` keeps building.

## Scope

- Add `.github/workflows/ci.yml` running `mvn -B verify` on JDK 21.
- Trigger on push to `main` and on all pull requests.
- Maven dependency cache enabled.

## Implementation notes

- Use `actions/setup-java@v4` with Temurin 21.
- No deploy, no publishing — build and test only.

## Acceptance criteria

- A green CI run appears on `main`.
- A PR that breaks a test shows a red check.

## Tests required

- n/a (workflow change); verified by observing the Actions run.

## Dependencies

_None._
