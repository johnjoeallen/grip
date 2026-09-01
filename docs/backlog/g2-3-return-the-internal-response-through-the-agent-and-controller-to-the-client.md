# G2.3 — Return the internal response through the Agent and Controller to the client

**Stage:** Stage 2 — Single proxied request

## Motivation

Complete the round trip.

## Scope

- Agent: send status + headers + body back over the channel.
- Controller: write that to the original external response and close the channel.
- Map an Agent-side failure (internal service unreachable) to a 502.

## Implementation notes

- Still single-channel and buffered is acceptable here.
- Strip hop-by-hop headers on the way out too.

## Acceptance criteria

- `GET /status` through the full chain returns `200 ok` to the HTTP client.
- `POST /echo` returns the posted bytes.
- Internal service down → client gets 502.

## Tests required

- `grip-integration-tests`: `EndToEndProxyTest` starting `TrivialHttpService` + Agent + Controller in one JVM; assert GET and POST round-trip and the 502 path.

## Dependencies

- G2.2
