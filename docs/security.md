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

## Detectability and acceptable use

GRIP is a tunnel. It carries arbitrary HTTP from the public Internet into a
private network over a persistent outbound connection. This is the point — and
it is also exactly what network security tooling is built to find.

- On an **egress-permissive** network (home, small office, CGNAT, a cloud VPC
  with NAT egress — no TLS-inspecting middlebox) GRIP-over-HTTPS looks like an
  ordinary long-lived TLS connection to a web host and works cleanly.
- On a network with a **decrypting next-generation firewall or secure web
  gateway** (Palo Alto, Zscaler, Netskope, Fortinet, …) plus threat
  prevention, GRIP is very likely to be **identified as a tunnel and may be
  blocked**. Once TLS is decrypted the traffic does not resemble web browsing:
  it is a long-lived connection carrying framed, nested HTTP requests and
  responses to a single external host, with the internal machine acting as a
  server — all strong tunnelling / C2 signatures. The transport framing does
  not change this; a reverse tunnel is identifiable as one regardless of how
  its bytes are shaped.

GRIP does **not**, and will **not**, attempt to disguise its traffic — no
domain fronting, no traffic mimicry, no timing jitter, no destination
rotation. Making a tunnel look like something else is red-team evasion and out
of scope.

Run GRIP only where you are **authorised** to expose the internal service:
either the network permits egress tunnels, or you administer the security
policy and have deliberately allowlisted the Controller domain (and, if TLS is
inspected, exempted it from decryption). An organisation that wants GRIP can
permit it on purpose; one that does not will detect it — which is the correct
outcome.

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
