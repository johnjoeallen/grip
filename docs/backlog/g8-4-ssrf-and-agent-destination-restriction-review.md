# G8.4 — SSRF and Agent destination restriction review

**Stage:** Stage 8 — Security hardening

## Motivation

The Agent sits inside the trusted network; it must stay a narrow door.

## Scope

- Confirm the Agent only ever calls its single configured `target-url` and never a destination derived from the proxied request.
- Reject a `target-url` that resolves to a surprising scope only if a future option allows request-derived targets — otherwise document why it's safe as-is.
- Log the fixed target at startup.

## Implementation notes

- Write an ADR-style note in `docs/security.md` on why v1 is not an SSRF vector and what a multi-service Agent would need (allow-list).

## Acceptance criteria

- No code path lets the request influence the internal destination.
- Security doc updated.

## Tests required

- Test: crafted requests (absolute-URI target, `Host` games, header injection) never change the internal destination.

## Dependencies

- G8.2
