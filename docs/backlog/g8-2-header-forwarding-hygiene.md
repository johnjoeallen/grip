# G8.2 — Header forwarding hygiene

**Stage:** Stage 8 — Security hardening

## Motivation

Proxies leak and confuse services if headers aren't handled carefully.

## Scope

- Strip hop-by-hop headers at every hop (`Connection` and everything it names, `Keep-Alive`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade`, `Proxy-*`).
- Set `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host` (and/or `Forwarded`) from the real external connection; drop client-supplied values.
- Forward the external `Host` value to the internal service only if configured to; default to the target's own host.

## Implementation notes

- Single `HeaderPolicy` used by both directions.
- Preserve header casing/multiplicity otherwise.

## Acceptance criteria

- Hop-by-hop headers never reach the internal service or the client.
- `X-Forwarded-*` reflect reality and cannot be spoofed by the client.

## Tests required

- `HeaderPolicyTest` + an e2e assertion via `/echo`-of-headers.

## Dependencies

- G7.2
