# G9.4 — Agent deployment guide

**Stage:** Stage 9 — Production packaging

## Motivation

The easy side, but still needs writing down.

## Scope

- `docs/deploy-agent.md`: install (jar/systemd/container), the three required settings, outbound-firewall note (443 to the Controller only), log locations, health endpoint.
- Troubleshooting: cert errors, wrong `agent-id`, Controller unreachable, target unreachable.

## Implementation notes

- Emphasise: no inbound rules, no port forwarding.
- Add to the Pages nav.

## Acceptance criteria

- A new operator can get an Agent connected from the guide alone.

## Tests required

- n/a (doc).

## Dependencies

- G9.1
