# Security

## Trust model (v1)

- The **Controller** is exposed to the Internet. It must be treated as a
  hardened public service.
- The **Agent** trusts the Controller it is configured to dial. It validates
  the Controller's TLS certificate normally.
- In v1 the Controller does **not** cryptographically authenticate Agents (no
  mTLS). Any host that can reach the Controller and complete a REGISTER for an
  unused Agent ID becomes that Agent. Deployments that need more must place the
  Controller's Agent-connect endpoint behind a network boundary or add one of
  the future mechanisms below.
- The Agent forwards requests to exactly **one configured internal URL**. It is
  not an open proxy.

## TLS

- Mandatory on the Agent→Controller connection.
- The Agent performs standard CA-chain validation and hostname verification.
  There is no "trust all certificates" switch, and one will not be added.
- `grip.controller-url` must be `https://`; `http://` is rejected at startup.
- Production uses publicly trusted certificates. Let's Encrypt is assumed:
  - `*.grip.example.com` (wildcard) for the Agent sub-domains, and
  - `controller.grip.example.com` for the Agent-facing hostname.
- Certificate issuance and renewal are handled **outside** GRIP — by an ACME
  client or a terminating reverse proxy. GRIP implements no certificate
  management.

## HTTP handling (to be hardened in Stage 8)

- **Host header** — used only to select the Agent; the forwarded request to the
  internal service uses the Agent's configured target host.
- **Hop-by-hop headers** (`Connection`, `Keep-Alive`, `Transfer-Encoding`,
  `Upgrade`, `TE`, `Trailer`, `Proxy-Authorization`, `Proxy-Authenticate`) are
  stripped at each hop and not forwarded.
- **`X-Forwarded-*` / `Forwarded`** — the Controller sets these to reflect the
  real external client; inbound values from clients are not trusted.
- **Request/response size** — frame size, header size, and body size limits are
  enforced so a single request cannot exhaust memory.
- **Connection limits** — caps on concurrent Agents, channels per connection,
  and queued bytes per channel.

## SSRF

The Agent only ever calls its single configured `grip.target-url`. It does not
take a destination from the proxied request. This keeps the Agent from becoming
an SSRF pivot into the private network. If multi-service Agents are added later,
an explicit allow-list is required.

## Denial of service

Considered in Stage 8:

- slow-loris style slow clients (bounded queues, idle timeouts);
- many idle channels (channel cap, idle-channel reaping);
- reconnect storms from misconfigured Agents (Controller-side backoff /
  rejection);
- oversized headers or bodies (hard limits, early rejection).

## Logging

- Request lines and header **names** may be logged at INFO.
- Header **values**, bodies, and credentials are not logged.
- Agent IDs and channel IDs are safe to log and are the primary correlation
  keys.

## Future security enhancements (not in v1)

Designed for, not yet built. None of these require changing the proxy protocol:

- **mTLS** between Agent and Controller.
- **Agent certificates** issued per Agent, with an enrolment / approval step.
- **Certificate pinning** of the Controller by the Agent.
- **Per-Agent credentials** (bearer token / shared secret) presented at
  REGISTER.
- **Admin API** to list, approve, and revoke Agents.
