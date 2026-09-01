---
title: Architecture
nav_order: 2
---

# Architecture

GRIP has two runtime components and one shared library.

| Component | Package | Responsibility |
|---|---|---|
| `grip-controller` | `dev.grip.controller` | Public edge. External HTTPS termination, Agent connection registry, sub-domain routing, channel bookkeeping, proxying. |
| `grip-agent` | `dev.grip.agent` | Private side. Outbound connection to the Controller, request forwarding to one internal service, reconnection. |
| `grip-protocol` | `dev.grip.protocol` | Shared vocabulary only — frame kinds, channel identity, limits. No transport code, no Controller/Agent types. |

## 1. Deployment topology

```mermaid
flowchart TB
    subgraph internet["Internet"]
        u1["client A"]
        u2["client B"]
    end

    subgraph edge["Public host — controller.grip.example.com"]
        lb["TLS / reverse proxy<br/>(Let's Encrypt, wildcard *.grip.example.com)"]
        ctl["grip-controller"]
        lb --> ctl
    end

    subgraph net1["Private network 1"]
        a1["grip-agent (alpha)"]
        s1["service :8080"]
        a1 --> s1
    end

    subgraph net2["Private network 2"]
        a2["grip-agent (beta)"]
        s2["service :3000"]
        a2 --> s2
    end

    u1 -->|"https://alpha.grip.example.com"| lb
    u2 -->|"https://beta.grip.example.com"| lb
    a1 -.->|"outbound TLS"| lb
    a2 -.->|"outbound TLS"| lb
```

- The Controller is the only component reachable from the Internet.
- TLS termination and certificate management may sit in a reverse proxy in
  front of the Controller, or in the Controller itself. GRIP does not issue or
  renew certificates.
- Each private network runs one Agent per exposed service. Agents need only
  outbound HTTPS to the Controller (normally port 443).

## 2. Agent connection establishment

```mermaid
sequenceDiagram
    participant A as GRIP Agent
    participant C as GRIP Controller

    A->>C: HTTPS connect (validate chain + hostname)
    A->>C: open long-lived stream to /grip/connect
    A->>C: REGISTER { agentId, version }
    C->>C: check id not already connected
    alt id free
        C-->>A: REGISTER_OK
        loop while connected
            A-->>C: PING (every heartbeat interval)
            C-->>A: PONG
        end
    else id in use
        C-->>A: REGISTER_REJECTED { reason }
        C->>A: close
    end
```

- The connection is **outbound from the Agent**. The Controller never dials the
  Agent.
- If the connection drops, the Agent reconnects with exponential backoff
  (`initial-backoff` → `max-backoff`).
- Missing heartbeats let either side detect a dead peer before TCP would.
- The exact REGISTER/heartbeat frames are a protocol detail fixed in Stage 3;
  the *lifecycle* above is decided.

## 3. External HTTP request flow

```mermaid
sequenceDiagram
    participant Cl as Client
    participant C as Controller
    participant A as Agent
    participant S as Internal service

    Cl->>C: GET https://alpha.grip.example.com/api/status
    C->>C: Host "alpha.<base-domain>" -> Agent "alpha" (connected?)
    C->>C: allocate channel 1001
    C->>A: REQUEST_START 1001 { method, path, headers }
    C->>A: REQUEST_END 1001
    A->>S: GET http://localhost:8080/api/status
    S-->>A: 200, headers, body
    A-->>C: RESPONSE_START 1001 { status, headers }
    A-->>C: RESPONSE_DATA 1001 (chunk...)
    A-->>C: RESPONSE_END 1001
    C-->>Cl: 200, headers, body
    C->>C: release channel 1001
```

The Controller keeps `channel id → external request/response`; the Agent keeps
`channel id → internal request/response`. Nothing is buffered end to end.

## 4. Multiplexed concurrent requests

One Agent connection, many channels:

```mermaid
sequenceDiagram
    participant C as Controller
    participant A as Agent

    C->>A: REQUEST_START 1001
    C->>A: REQUEST_START 1002
    C->>A: REQUEST_START 1003
    A-->>C: RESPONSE_START 1002
    A-->>C: RESPONSE_DATA 1002
    A-->>C: RESPONSE_START 1001
    A-->>C: RESPONSE_END 1002
    A-->>C: RESPONSE_DATA 1001
    A-->>C: RESPONSE_END 1001
    A-->>C: RESPONSE_START 1003
    A-->>C: RESPONSE_END 1003
```

- Channel IDs are allocated by the Controller, unique per connection, and
  reclaimable after close.
- Responses may start and finish in any order.
- Each channel is handled on its own virtual thread on both sides; the shared
  connection is written under a single lock (or a small write queue).

## 5. Client cancellation

```mermaid
sequenceDiagram
    participant Cl as Client
    participant C as Controller
    participant A as Agent
    participant S as Internal service

    Cl-xC: TCP close mid-request (channel 1001)
    C->>A: CANCEL 1001
    A->>S: abort internal request
    A-->>C: (no further frames for 1001)
    C->>C: release channel 1001
```

Cancellation also flows the other way: if the internal service fails or the
Agent shuts down, the Agent sends `CANCEL`/`ERROR` for its open channels and the
Controller ends the matching external responses.

## 6. Agent disconnection and reconnection

```mermaid
sequenceDiagram
    participant C as Controller
    participant A as Agent

    Note over C,A: connection lost
    C->>C: mark Agent "alpha" disconnected
    C->>C: fail all channels bound to that connection
    Note over C: requests to alpha.<base-domain> -> 502/503
    loop backoff: 1s, 2s, 4s ... max
        A->>C: reconnect attempt
    end
    A->>C: REGISTER { agentId: alpha }
    C-->>A: REGISTER_OK
    Note over C: routing to "alpha" restored
```

While an Agent is disconnected, requests for its sub-domain get a clear gateway
error rather than hanging. In-flight channels for a lost connection are failed
immediately and cleanly — no leaked state.

## Concurrency model

- Request handling uses **virtual threads**. A blocked internal call or a slow
  client costs a virtual thread, not a platform thread.
- The single GRIP connection per Agent is the one point of contention;
  writes to it are serialised.
- Synchronization is explicit and local (a registry map, a per-connection write
  path, per-channel state). No reactive pipeline unless streaming/backpressure
  work later proves it necessary.
