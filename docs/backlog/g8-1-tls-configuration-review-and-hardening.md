# G8.1 — TLS configuration review and hardening

**Stage:** Stage 8 — Security hardening

## Motivation

The public edge's TLS must be deliberately configured.

## Scope

- Document and set: minimum TLS 1.2 (prefer 1.3), a sane cipher list, OCSP stapling if terminating in-process.
- Confirm the Agent's validation cannot be weakened by any property.
- Document the terminating-proxy deployment as the recommended option and what GRIP expects behind it (`X-Forwarded-Proto` etc.).

## Implementation notes

- No code path that installs a permissive `SSLContext`.
- Add a startup assertion that the Agent is using the default trust manager.

## Acceptance criteria

- `docs/security.md` has a concrete TLS section.
- A test asserts no insecure `SSLContext` is reachable via config.

## Tests required

- Config-surface test: every `grip.*` and `server.ssl.*` combination still yields validated TLS on the Agent.

## Dependencies

- G7.5
