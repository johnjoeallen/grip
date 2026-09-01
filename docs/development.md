# Development

## Prerequisites

- JDK 21 or later
- Maven 3.9 or later

## Build

```bash
mvn verify          # compile + unit tests + integration tests
mvn -q -DskipTests package
```

Reactor modules:

| Module | Notes |
|---|---|
| `grip-protocol` | Plain library. No Spring. |
| `grip-controller` | Spring Boot app. `spring-boot:run` to start. |
| `grip-agent` | Spring Boot app. `spring-boot:run` to start. |
| `grip-integration-tests` | End-to-end tests and the `TrivialHttpService` fixture. Not published. |

## Run locally

```bash
# terminal 1 — internal service (anything on :8080); or use the test fixture
python3 -m http.server 8080

# terminal 2 — Controller
mvn -pl grip-controller spring-boot:run

# terminal 3 — Agent
mvn -pl grip-agent spring-boot:run
```

Until the proxying issues land, the services boot and expose health only.

## Configuration

### Controller (`grip.*`)

| Key | Default | Meaning |
|---|---|---|
| `grip.base-domain` | `grip.example.com` | Domain under which Agents are exposed. `alpha.<base-domain>` → Agent `alpha`. **Set this.** |
| `grip.connect.heartbeat-interval` | `15s` | Expected heartbeat cadence |
| `grip.connect.agent-timeout` | `45s` | No frame from an Agent for this long ⇒ connection closed |
| `grip.connect.reaper-interval` | `PT5S` | How often to scan for silent Agents (ISO-8601) |
| `server.port` | `8443` | Controller listen port |

### Agent (`grip.*`)

| Key | Default | Meaning |
|---|---|---|
| `grip.agent-id` | `alpha` | Name this Agent registers as |
| `grip.controller-url` | `https://controller.grip.example.com` | Controller base URL. Must be `https`. |
| `grip.target-url` | `http://localhost:8080` | The single internal service exposed |
| `grip.reconnect.initial-backoff` | `1s` | First retry delay |
| `grip.reconnect.max-backoff` | `30s` | Backoff ceiling |
| `grip.reconnect.heartbeat-interval` | `15s` | Heartbeat cadence on an idle connection |
| `grip.reconnect.heartbeat-timeout` | `45s` | No frame from the Controller for this long ⇒ drop and reconnect |
| `server.address` / `server.port` | `127.0.0.1` / `8081` | Localhost-only health endpoint |

Override with `application.yml`, environment variables
(`GRIP_BASE_DOMAIN`, `GRIP_AGENT_ID`, …), or `--grip.agent-id=…` on the
command line.

## Testing strategy

| Layer | Where | What |
|---|---|---|
| Unit | each module | Pure logic — routing, channel bookkeeping, backoff, framing codec |
| Protocol | `grip-protocol` + codec tests | Round-trip every frame kind; malformed input |
| Controller | `grip-controller` | Registry, sub-domain routing, channel lifecycle, error mapping |
| Agent | `grip-agent` | Connection lifecycle, reconnect/backoff, request forwarding, cancellation |
| End-to-end | `grip-integration-tests` | Start `TrivialHttpService` + Agent + Controller in one JVM; drive real HTTP through the whole chain |

The multiplexing end-to-end test must prove that two or more simultaneous
requests share one Agent connection with **no response data crossing
channels**, including when responses complete out of order.

## Code style

- Java 21, records and sealed types where they fit.
- Virtual threads for request handling.
- Explicit, local synchronization. No reactive framework unless a streaming
  requirement forces it.
- Keep modules honest: `grip-protocol` never imports Controller or Agent code.
