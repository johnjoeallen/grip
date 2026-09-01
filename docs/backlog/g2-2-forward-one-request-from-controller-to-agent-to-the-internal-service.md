# G2.2 — Forward one request from Controller to Agent to the internal service

**Stage:** Stage 2 — Single proxied request

## Motivation

The first vertical slice: a request leaves the Controller and reaches the internal service.

## Scope

- Controller: for a resolved Agent, send the request line + headers (and body, read fully for now) over the Agent connection on a channel id.
- Agent: receive it, make the matching request to `grip.target-url` using the JDK `HttpClient`, keep the internal response.
- **Simplification allowed:** permit only one in-flight channel per Agent connection in this issue if it makes the slice materially simpler; Stage 4 lifts this.

## Implementation notes

- A provisional encoding is fine; Stage 3 replaces it.
- Preserve method, path + query, and request headers minus hop-by-hop.
- Body may be buffered here; Stage 5 makes it streaming.

## Acceptance criteria

- A `GET /status` to `alpha.<base-domain>` reaches `TrivialHttpService` and the Agent obtains a 200/`ok`.

## Tests required

- End-to-end test (in `grip-integration-tests`) up to the Agent receiving the internal response; response-return is G2.3.

## Dependencies

- G2.1
- G1.5
