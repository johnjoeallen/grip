# G2.4 — End-to-end test harness for the full chain

**Stage:** Stage 2 — Single proxied request

## Motivation

The e2e test needs a repeatable way to stand up all three parts.

## Scope

- A JUnit fixture that starts `TrivialHttpService`, a Controller on a random port with a test TLS cert, and an Agent pointed at both.
- Helper to make external HTTP requests to `http(s)://<agent>.<test base-domain>:<port>` (host header override, not real DNS).
- Clean shutdown of all three between tests.

## Implementation notes

- Use a locally-generated CA/cert trusted only by the test Agent.
- Keep startup under a couple of seconds.

## Acceptance criteria

- `EndToEndProxyTest` uses the fixture and passes reliably in CI.

## Tests required

- The fixture itself has a smoke test; G2.3's tests build on it.

## Dependencies

- G2.3
