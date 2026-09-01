# G7.3 — Agent and Controller disconnect handling

**Stage:** Stage 7 — Failure handling

## Motivation

A lost connection must fail fast and recover.

## Scope

- Controller: on Agent connection loss, fail every bound channel (client gets 502/503) and clear registry state immediately.
- Agent: on Controller loss, cancel all in-flight internal requests, then reconnect (G1.5).
- No lingering channel or thread after either side drops.

## Implementation notes

- Assert no leaked virtual threads / map entries via test hooks or counters.

## Acceptance criteria

- Pull the Agent connection during 10 in-flight requests: all 10 clients get a prompt error, Agent reconnects, new requests succeed.

## Tests required

- e2e chaos test: drop the connection under load, assert clean failure + recovery + no leaks.

## Dependencies

- G7.1
- G1.5
