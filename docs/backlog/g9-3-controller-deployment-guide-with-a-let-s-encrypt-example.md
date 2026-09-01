# G9.3 — Controller deployment guide with a Let's Encrypt example

**Stage:** Stage 9 — Production packaging

## Motivation

The public edge is the fiddly part to deploy.

## Scope

- A `docs/deploy-controller.md` covering: DNS (`controller.` A/AAAA + `*.` wildcard), a terminating reverse proxy (Caddy or nginx) doing ACME for `*.grip.example.com` and `controller.grip.example.com`, and passing traffic to the Controller.
- Health/readiness wiring for a load balancer.
- Sizing and limit guidance.

## Implementation notes

- Show both 'proxy terminates TLS' and 'Controller terminates TLS' variants; recommend the proxy.
- Add to the Pages nav.

## Acceptance criteria

- Following the guide on a fresh host yields a working Controller with valid certs.

## Tests required

- n/a (doc); reviewed against a real deployment if possible.

## Dependencies

- G9.1
