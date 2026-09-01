#!/usr/bin/env python3
"""GRIP staged issue backlog.

Single source of truth for the implementation roadmap. Run with no arguments to
(re)write the ready-to-submit Markdown files under docs/backlog/. Run with
--create to also create the issues on GitHub (labels + milestones + issues,
in dependency order) using the `gh` CLI.

    python3 scripts/issues.py                 # write docs/backlog/*.md
    python3 scripts/issues.py --create        # + create GitHub issues
    python3 scripts/issues.py --create --dry-run
"""
from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parents[1]
BACKLOG = REPO / "docs" / "backlog"

STAGES = {
    0: "Stage 0 — Project foundation",
    1: "Stage 1 — Basic Agent connection",
    2: "Stage 2 — Single proxied request",
    3: "Stage 3 — GRIP framing protocol",
    4: "Stage 4 — Multiplexing",
    5: "Stage 5 — Streaming and backpressure",
    6: "Stage 6 — Host routing",
    7: "Stage 7 — Failure handling",
    8: "Stage 8 — Security hardening",
    9: "Stage 9 — Production packaging",
}


def I(key, stage, title, deps, motivation, scope, notes, acceptance, tests):
    return dict(key=key, stage=stage, title=title, deps=deps, motivation=motivation,
               scope=scope, notes=notes, acceptance=acceptance, tests=tests)


ISSUES = [
    # ---------------- Stage 0 ----------------
    I("G0.1", 0, "CI: build and test the reactor on every push and PR", [],
      "The skeleton is committed but nothing enforces that `main` keeps building.",
      ["Add `.github/workflows/ci.yml` running `mvn -B verify` on JDK 21.",
       "Trigger on push to `main` and on all pull requests.",
       "Maven dependency cache enabled."],
      ["Use `actions/setup-java@v4` with Temurin 21.",
       "No deploy, no publishing — build and test only."],
      ["A green CI run appears on `main`.",
       "A PR that breaks a test shows a red check."],
      ["n/a (workflow change); verified by observing the Actions run."]),

    I("G0.2", 0, "Publish the docs site to GitHub Pages", [],
      "The `docs/` Jekyll site should be live at the project Pages URL.",
      ["Add `.github/workflows/pages.yml` building `docs/` with "
       "`actions/jekyll-build-pages` and deploying via `actions/deploy-pages`.",
       "Enable Pages with source = GitHub Actions.",
       "Confirm the `just-the-docs` remote theme and Mermaid rendering work."],
      ["`docs/_config.yml` already sets `remote_theme` and `mermaid`.",
       "`baseurl` is `/grip`; check relative links resolve."],
      ["The site builds and deploys on a push touching `docs/`.",
       "Home, Architecture, Protocol, Security, Development, Roadmap are all "
       "reachable from the nav and Mermaid diagrams render."],
      ["n/a (workflow change); verified by loading the published site."]),

    I("G0.3", 0, "Issue templates and a PR checklist", [],
      "Contributions should arrive in the shape the roadmap expects.",
      ["Add `.github/ISSUE_TEMPLATE/` with a 'roadmap task' template mirroring "
       "the backlog fields (motivation, scope, implementation notes, acceptance "
       "criteria, tests, dependencies).",
       "Add `.github/pull_request_template.md` with the CONTRIBUTING checklist."],
      ["Keep templates short; link to `CONTRIBUTING.md`."],
      ["New issues and PRs open with the template pre-filled."],
      ["n/a."]),

    # ---------------- Stage 1 ----------------
    I("G1.1", 1, "Controller: accept a long-lived Agent connection stream", ["G0.1"],
      "The Controller needs an endpoint an Agent can hold open indefinitely.",
      ["Add an endpoint at `GripProtocol.AGENT_CONNECT_PATH` (`/grip/connect`) "
       "that accepts a streaming request and keeps the response open.",
       "Hand the open byte streams to a connection handler object; do not parse "
       "GRIP frames yet (that is Stage 3) — a placeholder line protocol is fine.",
       "One connection object per open stream; log connect/disconnect."],
      ["Use Spring MVC with `StreamingResponseBody` / async request handling on "
       "virtual threads, or a raw servlet — whichever keeps both directions "
       "streamable.",
       "No Agent identity or registry yet (G1.3)."],
      ["An Agent-side test client can open the stream, hold it, send bytes, and "
       "receive bytes.",
       "Closing either side is detected and logged within the heartbeat window."],
      ["Controller test: open the endpoint, exchange bytes, assert both "
       "directions stream without buffering to EOF."]),

    I("G1.2", 1, "Agent: outbound TLS connection with strict validation", ["G0.1"],
      "The Agent must dial the Controller securely and never with validation off.",
      ["On startup, open a connection to `grip.controller-url` + "
       "`/grip/connect` using the JDK `HttpClient` (HTTP/2 allowed, must also "
       "work over HTTP/1.1).",
       "Full certificate-chain and hostname verification — no custom "
       "`TrustManager`, no `HostnameVerifier` override, no config flag to "
       "disable it.",
       "Reject a non-`https` `controller-url` at startup (already enforced in "
       "`GripAgentProperties`); add a startup log line naming the target."],
      ["Keep the connection handle in a single `AgentConnection` component.",
       "Connection lifecycle states: CONNECTING, CONNECTED, DISCONNECTED."],
      ["Against a TLS test Controller with a trusted cert, the Agent connects.",
       "Against a self-signed / wrong-hostname cert, the Agent fails to connect "
       "with a clear error and does **not** proceed."],
      ["Agent test with a locally-trusted self-signed CA: success path.",
       "Agent test with an untrusted cert: connection rejected, no insecure "
       "fallback."]),

    I("G1.3", 1, "REGISTER handshake, Agent identity, and the Agent registry", ["G1.1", "G1.2"],
      "The Controller must know which Agent is on which connection.",
      ["Define a minimal REGISTER exchange: Agent sends its `agent-id` and "
       "protocol version right after connecting; Controller replies OK or "
       "REJECTED.",
       "Add an in-memory `AgentRegistry` on the Controller: `agentId -> "
       "AgentConnection`.",
       "Register on REGISTER_OK; deregister on disconnect."],
      ["This is a provisional encoding; Stage 3 formalises it. Keep it a single "
       "small framed message so Stage 3 can slot in.",
       "Reject unknown protocol versions."],
      ["After connect+register, `AgentRegistry.get(\"alpha\")` returns the live "
       "connection.",
       "After disconnect, it returns empty."],
      ["Controller test: register two Agents, assert both are tracked; drop one, "
       "assert only it is removed."]),

    I("G1.4", 1, "Heartbeat and dead-peer detection", ["G1.3"],
      "TCP alone is too slow to notice a silently dead peer.",
      ["Agent sends `PING` every `grip.reconnect.heartbeat-interval` on an idle "
       "connection; Controller replies `PONG`.",
       "Controller marks an Agent dead if no frame arrives within "
       "`grip.connect.agent-timeout` and closes the connection.",
       "Agent treats a missing `PONG` within a timeout as a dead connection and "
       "closes it (triggering reconnect, G1.5)."],
      ["Reuse the `FrameType.PING`/`PONG` concepts.",
       "Scheduled work on a single-thread scheduler; keep it simple."],
      ["Killing the Controller's socket without a FIN is detected within "
       "`agent-timeout`.",
       "A healthy idle connection stays up indefinitely."],
      ["Controller test: simulate a stalled Agent, assert it is reaped after "
       "the timeout.",
       "Agent test: simulate missing PONGs, assert the connection is dropped."]),

    I("G1.5", 1, "Agent reconnect with exponential backoff and graceful shutdown", ["G1.4"],
      "A dropped connection must recover on its own without hammering the Controller.",
      ["On any disconnect, the Agent retries: `initial-backoff`, doubling to "
       "`max-backoff`, with small jitter.",
       "Reset the backoff after a connection stays up for a stable period.",
       "On SIGTERM, the Agent sends a clean disconnect and stops retrying."],
      ["A single reconnect loop, not one per failure.",
       "Log each attempt at DEBUG, give up only on fatal config errors (e.g. "
       "bad URL), not on transient failures."],
      ["Bring the Controller down and up; the Agent reconnects and re-registers "
       "with increasing then reset backoff.",
       "`kill -TERM` on the Agent exits within a couple of seconds with a "
       "disconnect logged."],
      ["Agent test: fake transport that fails N times then succeeds; assert "
       "delays follow the backoff schedule and registration eventually "
       "succeeds."]),

    I("G1.6", 1, "Duplicate Agent ID handling", ["G1.3"],
      "Two Agents claiming the same id must resolve deterministically.",
      ["Decide and implement the policy: reject the second REGISTER while the "
       "first is connected (recommended), returning REJECTED with a reason.",
       "Ensure a reconnecting Agent whose previous connection is half-open can "
       "still take over once the stale one is reaped."],
      ["Document the chosen policy in `docs/protocol.md` under REGISTER.",
       "Consider a short grace window so a fast reconnect after a network blip "
       "isn't rejected forever."],
      ["Second concurrent REGISTER for `alpha` is rejected with a clear reason.",
       "After the first connection is reaped, `alpha` can register again."],
      ["Controller test: connect `alpha`, attempt a second `alpha`, assert "
       "rejection; reap the first, assert the second now succeeds."]),

    # ---------------- Stage 2 ----------------
    I("G2.1", 2, "Controller: resolve external Host to a connected Agent", ["G1.3"],
      "Routing is the Controller's first proxy responsibility.",
      ["Parse the external request `Host`; strip port; lower-case.",
       "Expect `<agentId>.<grip.base-domain>`; extract `agentId`.",
       "Look up `AgentRegistry`. If missing → 404; if the host isn't under "
       "`base-domain` → 404; if the Agent id is known but disconnected → 503.",
       "Return a small JSON/plain error body for each case."],
      ["`base-domain` comes from `GripControllerProperties`; never hard-coded.",
       "Full wildcard routing polish is Stage 6 — here just the happy path plus "
       "the three error cases."],
      ["`alpha.<base-domain>` with `alpha` connected resolves to its connection.",
       "Unknown/disconnected/off-domain hosts return 404/503/404 respectively."],
      ["Controller unit test over a table of hosts → expected outcome."]),

    I("G2.2", 2, "Forward one request from Controller to Agent to the internal service", ["G2.1", "G1.5"],
      "The first vertical slice: a request leaves the Controller and reaches the internal service.",
      ["Controller: for a resolved Agent, send the request line + headers (and "
       "body, read fully for now) over the Agent connection on a channel id.",
       "Agent: receive it, make the matching request to `grip.target-url` using "
       "the JDK `HttpClient`, keep the internal response.",
       "**Simplification allowed:** permit only one in-flight channel per Agent "
       "connection in this issue if it makes the slice materially simpler; "
       "Stage 4 lifts this."],
      ["A provisional encoding is fine; Stage 3 replaces it.",
       "Preserve method, path + query, and request headers minus hop-by-hop.",
       "Body may be buffered here; Stage 5 makes it streaming."],
      ["A `GET /status` to `alpha.<base-domain>` reaches `TrivialHttpService` "
       "and the Agent obtains a 200/`ok`."],
      ["End-to-end test (in `grip-integration-tests`) up to the Agent receiving "
       "the internal response; response-return is G2.3."]),

    I("G2.3", 2, "Return the internal response through the Agent and Controller to the client", ["G2.2"],
      "Complete the round trip.",
      ["Agent: send status + headers + body back over the channel.",
       "Controller: write that to the original external response and close the "
       "channel.",
       "Map an Agent-side failure (internal service unreachable) to a 502."],
      ["Still single-channel and buffered is acceptable here.",
       "Strip hop-by-hop headers on the way out too."],
      ["`GET /status` through the full chain returns `200 ok` to the HTTP "
       "client.",
       "`POST /echo` returns the posted bytes.",
       "Internal service down → client gets 502."],
      ["`grip-integration-tests`: `EndToEndProxyTest` starting "
       "`TrivialHttpService` + Agent + Controller in one JVM; assert GET and "
       "POST round-trip and the 502 path."]),

    I("G2.4", 2, "End-to-end test harness for the full chain", ["G2.3"],
      "The e2e test needs a repeatable way to stand up all three parts.",
      ["A JUnit fixture that starts `TrivialHttpService`, a Controller on a "
       "random port with a test TLS cert, and an Agent pointed at both.",
       "Helper to make external HTTP requests to `http(s)://<agent>.<test "
       "base-domain>:<port>` (host header override, not real DNS).",
       "Clean shutdown of all three between tests."],
      ["Use a locally-generated CA/cert trusted only by the test Agent.",
       "Keep startup under a couple of seconds."],
      ["`EndToEndProxyTest` uses the fixture and passes reliably in CI."],
      ["The fixture itself has a smoke test; G2.3's tests build on it."]),

    # ---------------- Stage 3 ----------------
    I("G3.1", 3, "Design and document the GRIP wire format", ["G2.3"],
      "Channels and streaming need a real frame format, not the Stage 2 placeholder.",
      ["Specify a binary, length-prefixed frame: type tag, channel id, flags, "
       "payload length, payload.",
       "Specify how `REQUEST_START`/`RESPONSE_START` carry method/path/status "
       "and headers.",
       "Specify REGISTER, PING/PONG, CANCEL, ERROR (with an error-code enum).",
       "Write it all into `docs/protocol.md`, moving items from 'deferred' to "
       "'decided'."],
      ["Keep it minimal — no settings negotiation yet unless clearly needed.",
       "Must be codec-able over a plain HTTP/1.1 stream; no HTTP/2-only "
       "assumptions."],
      ["`docs/protocol.md` fully describes every frame currently in "
       "`FrameType` plus REGISTER, with byte layouts.",
       "The design is reviewed and agreed on the issue before coding G3.2."],
      ["n/a (design doc)."]),

    I("G3.2", 3, "grip-protocol: frame types and codec", ["G3.1"],
      "A tested encoder/decoder both services share.",
      ["Add `Frame` types (sealed interface + records per kind) in "
       "`dev.grip.protocol`.",
       "Add `FrameCodec` — encode to bytes, decode from a byte stream, "
       "incremental (handles partial reads).",
       "Enforce `MAX_FRAME_PAYLOAD_BYTES` / `MAX_HEADER_BYTES`; reject "
       "over-limit or malformed frames with a typed exception."],
      ["No Spring, no I/O framework — operate on `ByteBuffer` / `byte[]` / "
       "`InputStream`.",
       "Zero-copy where reasonable for payloads."],
      ["Every frame kind round-trips: encode → decode → equal.",
       "Truncated and oversized inputs raise the codec's exception, not a "
       "generic one."],
      ["`grip-protocol` codec tests: round-trip table for all frames; "
       "fuzz/boundary tests for truncation, zero-length, max-length, bad type "
       "tag."]),

    I("G3.3", 3, "Controller: run the connection on the real codec", ["G3.2"],
      "Replace the Stage 2 placeholder on the Controller side.",
      ["Read/write `Frame`s via `FrameCodec` on the Agent connection.",
       "REGISTER, PING/PONG, and the single-channel request/response path all "
       "go through frames now.",
       "A malformed frame closes the connection with a logged protocol error."],
      ["Keep the single-channel restriction if G2.2 took it; G4 removes it.",
       "One reader loop per connection; writes still serialised."],
      ["Stage 2's e2e tests still pass, now over real frames.",
       "Feeding garbage bytes closes the connection cleanly."],
      ["Controller test: malformed frame → connection closed, registry updated."]),

    I("G3.4", 3, "Agent: run the connection on the real codec", ["G3.2"],
      "Replace the Stage 2 placeholder on the Agent side.",
      ["Read/write `Frame`s via `FrameCodec`.",
       "REGISTER/heartbeat/request/response all framed.",
       "A malformed frame from the Controller drops the connection → reconnect."],
      ["Mirror G3.3.",
       "Reader on its own virtual thread; heartbeat scheduler unchanged."],
      ["Stage 2 e2e tests pass over frames from both ends.",
       "Malformed input from the Controller triggers a clean reconnect."],
      ["Agent test: malformed frame → reconnect; valid frames → normal flow."]),

    I("G3.5", 3, "Wire CANCEL and ERROR end to end", ["G3.3", "G3.4"],
      "Channels need explicit abnormal-termination semantics before multiplexing.",
      ["Controller sends `CANCEL` for a channel when it decides to abandon it.",
       "Agent sends `ERROR` (with code) when the internal request fails, and "
       "handles inbound `CANCEL` by aborting the internal request.",
       "Both sides free channel state on CANCEL/ERROR/RESPONSE_END."],
      ["Define the error-code enum in `grip-protocol` (from G3.1).",
       "Idempotent: a second CANCEL for a freed channel is ignored."],
      ["An Agent `ERROR` becomes a 502 to the external client.",
       "A Controller `CANCEL` stops the Agent's internal request."],
      ["e2e: internal 500 → client 502 with the error surfaced in logs.",
       "e2e: Controller cancels mid-flight → Agent aborts (observable via the "
       "`/slow` endpoint)."]),

    # ---------------- Stage 4 ----------------
    I("G4.1", 4, "Controller: channel allocation and channel→exchange map", ["G3.5"],
      "Many concurrent external requests over one Agent connection.",
      ["Allocate a unique `ChannelId` per external request on a connection.",
       "Maintain `channel -> external exchange` with its lifecycle state.",
       "Reclaim ids after the channel closes; enforce "
       "`MAX_CHANNELS_PER_CONNECTION`.",
       "Remove the Stage 2 single-channel restriction on the Controller."],
      ["Monotonic allocation with a free set for reuse is fine.",
       "Guard the map with explicit locking or a concurrent map + per-channel "
       "state object."],
      ["100 concurrent requests to one Agent get 100 distinct channels.",
       "Exceeding the cap yields a 503, not a crash."],
      ["Controller test: concurrent allocation is unique and thread-safe; cap "
       "is enforced; ids are reused after close."]),

    I("G4.2", 4, "Agent: channel→internal-exchange map", ["G3.5"],
      "The Agent side of multiplexing.",
      ["Maintain `channel -> internal request/response` state.",
       "Each channel's internal call runs on its own virtual thread.",
       "Remove the Stage 2 single-channel restriction on the Agent."],
      ["Cancellation must reach the right virtual thread (interrupt / request "
       "abort).",
       "No shared mutable buffers between channels."],
      ["Multiple channels proxy concurrently through one Agent.",
       "Cancelling one channel does not disturb the others."],
      ["Agent test: interleaved frames for 3 channels produce 3 correct, "
       "independent internal calls."]),

    I("G4.3", 4, "Serialised connection writer", ["G4.1", "G4.2"],
      "Frames from many channels share one connection and must not interleave mid-frame.",
      ["A single writer per connection: a queue that channels submit whole "
       "frames to, drained by one writer thread/loop.",
       "Fairness across channels (round-robin or FIFO with per-channel limits).",
       "Backpressure hook for Stage 5 (bounded queue)."],
      ["Whole frames are the unit of atomicity; a large body is already split "
       "into `*_DATA` frames by the codec/limits.",
       "Applies on both Controller and Agent."],
      ["Under load from many channels, no frame is ever split by another "
       "frame's bytes.",
       "One slow channel cannot starve others indefinitely."],
      ["Concurrency test: N producers, one connection, decode the output and "
       "assert every frame is intact and attributable."]),

    I("G4.4", 4, "Multiplexing correctness test: no cross-channel leakage", ["G4.3"],
      "The core guarantee of GRIP must be proven, including out-of-order completion.",
      ["e2e test: fire 2+ simultaneous requests to one Agent, with the internal "
       "service returning distinct, identifiable bodies and varying delays so "
       "responses complete out of order.",
       "Assert each external client receives exactly its own response, "
       "byte-for-byte.",
       "Repeat under a stress loop (e.g. 50 concurrent × 20 iterations)."],
      ["Use `/echo` with a per-request marker and `/slow?ms=` to force "
       "reordering.",
       "Run in CI but keep the stress count modest."],
      ["Zero cross-channel contamination across the stress run.",
       "Responses observed completing in a different order than requested."],
      ["`MultiplexingE2ETest` in `grip-integration-tests`."]),

    # ---------------- Stage 5 ----------------
    I("G5.1", 5, "Stream request bodies as frames", ["G4.4"],
      "Large uploads must not be buffered in memory.",
      ["Controller reads the external request body incrementally and emits "
       "`REQUEST_DATA` frames up to `MAX_FRAME_PAYLOAD_BYTES`, then "
       "`REQUEST_END`.",
       "Agent writes received `REQUEST_DATA` straight to the internal request "
       "body as it arrives.",
       "Remove any full-body buffering from Stage 2."],
      ["Bounded read-ahead per channel.",
       "Handle `Content-Length` and chunked transfer on both ends."],
      ["A multi-hundred-MB upload proxies with bounded Controller and Agent "
       "heap.",
       "Interrupted mid-upload → CANCEL/ERROR, no leak."],
      ["e2e: stream a large generated body to `/echo`, assert equality and "
       "cap heap via a small `-Xmx` in the test JVM."]),

    I("G5.2", 5, "Stream response bodies as frames", ["G4.4"],
      "Large downloads and slow producers must not be buffered.",
      ["Agent reads the internal response body incrementally into "
       "`RESPONSE_DATA` frames, then `RESPONSE_END`.",
       "Controller writes them to the external response as they arrive and "
       "flushes.",
       "Support an internal service that streams slowly / indefinitely (SSE-"
       "like)."],
      ["Bounded buffering per channel.",
       "Flush semantics so the client sees data promptly."],
      ["A large / slow / unbounded internal response reaches the client "
       "incrementally with bounded heap on both sides."],
      ["e2e: `/slow`-style trickle endpoint; assert the client receives bytes "
       "before the response completes."]),

    I("G5.3", 5, "Per-channel flow control and bounded queues", ["G5.1", "G5.2"],
      "A slow reader on one side must slow the producer on the other, not fill memory.",
      ["Bounded queue of pending bytes/frames per channel in the connection "
       "writer.",
       "When a channel's queue is full, pause reading from that channel's "
       "source (external body read, or internal response read).",
       "Resume when it drains. Choose credit-based windows or simple "
       "high/low-watermark pausing — document the choice in `protocol.md`."],
      ["Must not deadlock: pausing one channel cannot block the shared reader "
       "for others.",
       "Interacts with G4.3 fairness."],
      ["A slow external client causes the internal read to pause; heap stays "
       "bounded; other channels keep flowing."],
      ["Test: slow-consuming client + fast internal producer; assert bounded "
       "queued bytes and continued progress on a second channel."]),

    I("G5.4", 5, "Enforce protocol limits", ["G5.3"],
      "Every limit in `GripProtocol` must actually be enforced.",
      ["Frame payload size, header block size, concurrent channels per "
       "connection, queued bytes per channel, total connections.",
       "Over-limit → a defined `ERROR` code (channel) or connection close "
       "(connection-level), never an OOM.",
       "Make the limits configurable with the current constants as defaults."],
      ["Revisit the provisional numbers in `GripProtocol` and set sensible "
       "production defaults.",
       "Surface limits in `docs/development.md`."],
      ["Each limit has a test that exceeds it and observes the defined "
       "rejection.",
       "No limit breach can crash a service."],
      ["Limit-enforcement test suite covering every limit."]),

    I("G5.5", 5, "Memory-safety test under adversarial clients", ["G5.4"],
      "Prove the slow/greedy client cannot cause unbounded growth.",
      ["A test that runs many slow-loris-style clients plus large bodies "
       "against a heap-capped Controller, for a sustained period.",
       "Assert steady-state heap and no OOM."],
      ["Keep runtime CI-friendly (a minute or two).",
       "Document the observed ceilings."],
      ["The scenario runs green with a tight `-Xmx`."],
      ["`MemorySafetyE2ETest` (may be tagged slow / opt-in in CI)."]),

    # ---------------- Stage 6 ----------------
    I("G6.1", 6, "Configurable wildcard-domain routing", ["G4.4"],
      "Routing should be driven entirely by `grip.base-domain`.",
      ["Extract routing into a `HostRouter` component: host → agentId.",
       "Support an optional list of additional accepted base domains.",
       "Case-insensitive, port-stripping, trailing-dot tolerant."],
      ["No `example.com` anywhere in code or defaults beyond the sample "
       "`application.yml`.",
       "Reject a host whose label count/shape doesn't match "
       "`<agent>.<base-domain>`."],
      ["`alpha.<base-domain>` → `alpha` across a table of tricky inputs.",
       "Changing `base-domain` in config changes routing with no code change."],
      ["`HostRouterTest` with a wide input table."]),

    I("G6.2", 6, "Routing edge cases: unknown, disconnected, invalid, reserved, duplicate", ["G6.1", "G1.6"],
      "Each failure mode should be explicit and testable.",
      ["Unknown agentId → 404. Known but disconnected → 503 with a "
       "`Retry-After` hint. Host not under any base domain → 404. Malformed "
       "host → 400.",
       "Reserve names (`www`, `api`, `controller`, health hostnames) — "
       "configurable reserved list, never routed to an Agent.",
       "Duplicate-Agent behaviour from G1.6 reflected here."],
      ["Bodies are small `application/problem+json`.",
       "Log at INFO with agentId + reason."],
      ["Every case returns its defined status and body.",
       "A reserved name cannot be claimed by an Agent."],
      ["Controller test table: host → (status, code)."]),

    I("G6.3", 6, "Routing end-to-end tests", ["G6.2"],
      "Confirm routing works through the whole chain, not just in isolation.",
      ["e2e: two Agents (`alpha`, `beta`) → two internal services; assert each "
       "sub-domain hits the right one.",
       "e2e: disconnect `beta`, assert its sub-domain returns 503 while "
       "`alpha` still works."],
      ["Reuse the G2.4 fixture, extended to N Agents."],
      ["Multi-Agent routing verified end to end, including the disconnect "
       "case."],
      ["`RoutingE2ETest`."]),

    # ---------------- Stage 7 ----------------
    I("G7.1", 7, "External client disconnect cancels the channel", ["G4.4"],
      "Abandoned requests must not keep working.",
      ["Detect external client disconnect (broken output stream / async "
       "error).",
       "Send `CANCEL` for that channel; free Controller state.",
       "Agent aborts the internal request on `CANCEL`."],
      ["Debounce: don't CANCEL a channel that already completed.",
       "Covered partly by G3.5; this is the client-triggered path + tests."],
      ["Killing the client during `/slow` causes the Agent's internal request "
       "to abort within a short window."],
      ["e2e: client disconnect mid-`/slow`; assert Agent-side abort (probe via "
       "a counter on the internal service)."]),

    I("G7.2", 7, "Internal service failure handling", ["G3.5"],
      "The internal service can be down, slow, or drop mid-response.",
      ["Connect failure → `ERROR`/502. Read timeout → `ERROR`/504. "
       "Mid-response disconnect → `ERROR`, truncate the external response "
       "cleanly (or reset the stream).",
       "Configurable internal connect/read timeouts on the Agent."],
      ["Distinguish 'never started' (safe to 502) from 'partially sent' "
       "(cannot change status)."],
      ["Each internal failure mode maps to the defined external outcome.",
       "No hung channels afterwards."],
      ["Agent + e2e tests for connect-refused, timeout, and mid-stream drop."]),

    I("G7.3", 7, "Agent and Controller disconnect handling", ["G7.1", "G1.5"],
      "A lost connection must fail fast and recover.",
      ["Controller: on Agent connection loss, fail every bound channel "
       "(client gets 502/503) and clear registry state immediately.",
       "Agent: on Controller loss, cancel all in-flight internal requests, "
       "then reconnect (G1.5).",
       "No lingering channel or thread after either side drops."],
      ["Assert no leaked virtual threads / map entries via test hooks or "
       "counters."],
      ["Pull the Agent connection during 10 in-flight requests: all 10 "
       "clients get a prompt error, Agent reconnects, new requests succeed."],
      ["e2e chaos test: drop the connection under load, assert clean failure + "
       "recovery + no leaks."]),

    I("G7.4", 7, "Timeouts and malformed-frame handling", ["G3.3", "G3.4"],
      "Bound every wait; reject every bad input.",
      ["Timeouts: Agent register, idle connection (heartbeat), internal "
       "connect/read, whole-request ceiling (configurable, optional).",
       "Malformed frame → connection close with a protocol `ERROR` logged; "
       "never a partial-state hang.",
       "Oversized REGISTER / header block → reject before allocating."],
      ["Centralise timeout config under `grip.*`.",
       "One place that maps codec exceptions → connection teardown."],
      ["Each timeout fires and is logged with context.",
       "A corpus of malformed frames all produce clean closes."],
      ["Timeout tests per case; malformed-frame corpus test."]),

    I("G7.5", 7, "Graceful shutdown of both services", ["G7.3"],
      "SIGTERM should drain, not sever.",
      ["Controller: stop accepting new external requests and new Agent "
       "connections; let in-flight channels finish within a grace period, then "
       "`CANCEL` the rest and close.",
       "Agent: stop accepting new channels; finish or cancel in-flight; send a "
       "clean disconnect; exit.",
       "Grace period configurable."],
      ["Hook Spring's lifecycle / `SmartLifecycle` or a shutdown hook.",
       "Return non-zero only on a genuinely dirty exit."],
      ["`kill -TERM` during load: in-flight requests within the grace window "
       "complete; the rest end cleanly; process exits 0."],
      ["e2e: shutdown under load, assert the drain behaviour and exit code."]),

    # ---------------- Stage 8 ----------------
    I("G8.1", 8, "TLS configuration review and hardening", ["G7.5"],
      "The public edge's TLS must be deliberately configured.",
      ["Document and set: minimum TLS 1.2 (prefer 1.3), a sane cipher list, "
       "OCSP stapling if terminating in-process.",
       "Confirm the Agent's validation cannot be weakened by any property.",
       "Document the terminating-proxy deployment as the recommended option "
       "and what GRIP expects behind it (`X-Forwarded-Proto` etc.)."],
      ["No code path that installs a permissive `SSLContext`.",
       "Add a startup assertion that the Agent is using the default trust "
       "manager."],
      ["`docs/security.md` has a concrete TLS section.",
       "A test asserts no insecure `SSLContext` is reachable via config."],
      ["Config-surface test: every `grip.*` and `server.ssl.*` combination "
       "still yields validated TLS on the Agent."]),

    I("G8.2", 8, "Header forwarding hygiene", ["G7.2"],
      "Proxies leak and confuse services if headers aren't handled carefully.",
      ["Strip hop-by-hop headers at every hop (`Connection` and everything it "
       "names, `Keep-Alive`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade`, "
       "`Proxy-*`).",
       "Set `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host` (and/or "
       "`Forwarded`) from the real external connection; drop client-supplied "
       "values.",
       "Forward the external `Host` value to the internal service only if "
       "configured to; default to the target's own host."],
      ["Single `HeaderPolicy` used by both directions.",
       "Preserve header casing/multiplicity otherwise."],
      ["Hop-by-hop headers never reach the internal service or the client.",
       "`X-Forwarded-*` reflect reality and cannot be spoofed by the client."],
      ["`HeaderPolicyTest` + an e2e assertion via `/echo`-of-headers."]),

    I("G8.3", 8, "Limits and DoS review", ["G5.4", "G7.4"],
      "Confirm the service degrades, not collapses, under abuse.",
      ["Review every limit for a sane production default: max connections, "
       "channels/connection, header bytes, body bytes (optional cap), queued "
       "bytes, request rate per Agent connection attempt.",
       "Add connection-accept backpressure on the Controller.",
       "Reconnect-storm protection: Controller-side rejection/backoff for an "
       "Agent id that reconnects too fast."],
      ["Everything configurable; defaults documented.",
       "Prefer shedding load (503) over unbounded queueing."],
      ["A load test at 2–3× expected concurrency stays responsive for "
       "healthy traffic.",
       "A reconnect storm from one bad Agent doesn't affect others."],
      ["Load/abuse test suite (opt-in in CI)."]),

    I("G8.4", 8, "SSRF and Agent destination restriction review", ["G8.2"],
      "The Agent sits inside the trusted network; it must stay a narrow door.",
      ["Confirm the Agent only ever calls its single configured `target-url` "
       "and never a destination derived from the proxied request.",
       "Reject a `target-url` that resolves to a surprising scope only if a "
       "future option allows request-derived targets — otherwise document why "
       "it's safe as-is.",
       "Log the fixed target at startup."],
      ["Write an ADR-style note in `docs/security.md` on why v1 is not an SSRF "
       "vector and what a multi-service Agent would need (allow-list)."],
      ["No code path lets the request influence the internal destination.",
       "Security doc updated."],
      ["Test: crafted requests (absolute-URI target, `Host` games, header "
       "injection) never change the internal destination."]),

    # ---------------- Stage 9 ----------------
    I("G9.1", 9, "Executable jars and runnable configuration examples", ["G8.1"],
      "Operators need artifacts and known-good config.",
      ["`spring-boot:repackage` for Controller and Agent; document the jar "
       "names and `java -jar` invocation.",
       "`examples/` with a complete Controller `application.yml` and Agent "
       "`application.yml` for a realistic deployment.",
       "A `docker-compose`-free local demo script that starts a Controller, an "
       "Agent, and a sample service."],
      ["Keep examples free of real hostnames; use `grip.example.com` clearly "
       "marked as a placeholder."],
      ["`java -jar` starts each service from an example config.",
       "The demo script brings up a working proxy locally."],
      ["Smoke test that the repackaged jars boot."]),

    I("G9.2", 9, "systemd units and Docker images", ["G9.1"],
      "Two standard deployment shapes.",
      ["`examples/systemd/grip-controller.service` and `grip-agent.service` "
       "(hardened: `DynamicUser`, `ProtectSystem`, restart policy).",
       "Dockerfiles for both (distroless or `-jammy` JRE base), plus a "
       "`compose.yaml` for the Agent-side (Agent + sample service).",
       "Publish images from CI on tags (optional, behind a flag)."],
      ["Non-root in the containers.",
       "Document env-var configuration for the container case."],
      ["A container Agent connects to a container Controller and proxies.",
       "The systemd units start/stop/restart cleanly."],
      ["CI builds the images; a compose-based e2e smoke test (opt-in)."]),

    I("G9.3", 9, "Controller deployment guide with a Let's Encrypt example", ["G9.1"],
      "The public edge is the fiddly part to deploy.",
      ["A `docs/deploy-controller.md` covering: DNS (`controller.` A/AAAA + "
       "`*.` wildcard), a terminating reverse proxy (Caddy or nginx) doing "
       "ACME for `*.grip.example.com` and `controller.grip.example.com`, and "
       "passing traffic to the Controller.",
       "Health/readiness wiring for a load balancer.",
       "Sizing and limit guidance."],
      ["Show both 'proxy terminates TLS' and 'Controller terminates TLS' "
       "variants; recommend the proxy.",
       "Add to the Pages nav."],
      ["Following the guide on a fresh host yields a working Controller with "
       "valid certs."],
      ["n/a (doc); reviewed against a real deployment if possible."]),

    I("G9.4", 9, "Agent deployment guide", ["G9.1"],
      "The easy side, but still needs writing down.",
      ["`docs/deploy-agent.md`: install (jar/systemd/container), the three "
       "required settings, outbound-firewall note (443 to the Controller "
       "only), log locations, health endpoint.",
       "Troubleshooting: cert errors, wrong `agent-id`, Controller "
       "unreachable, target unreachable."],
      ["Emphasise: no inbound rules, no port forwarding.",
       "Add to the Pages nav."],
      ["A new operator can get an Agent connected from the guide alone."],
      ["n/a (doc)."]),

    I("G9.5", 9, "Operational endpoints and logging", ["G9.1"],
      "Running services need to be observable.",
      ["Controller: health, readiness, and a small status endpoint (connected "
       "Agents, active channels) — bound appropriately, not on the public "
       "vhost.",
       "Agent: health/readiness incl. connection state and last-heartbeat.",
       "Structured request logging (agentId, channel, method, path, status, "
       "bytes, duration) with no sensitive values.",
       "Metrics via Micrometer (counts, gauges, timers) — no new "
       "infrastructure, just the registry beans."],
      ["Keep the status endpoint read-only and access-controlled by network "
       "placement.",
       "Document every field."],
      ["Health/readiness reflect real state (e.g. Agent readiness false while "
       "disconnected).",
       "Logs and metrics cover a request's life."],
      ["Endpoint tests; a log-assertion test for the request log shape."]),
]


def slug(issue):
    base = re.sub(r"[^a-z0-9]+", "-", issue["title"].lower()).strip("-")
    return f"{issue['key'].lower().replace('.', '-')}-{base}"


def body(issue, numbers):
    dep_lines = []
    for d in issue["deps"]:
        n = numbers.get(d)
        dep_lines.append(f"- {d}" + (f" (#{n})" if n else ""))
    dep_block = "\n".join(dep_lines) if dep_lines else "_None._"

    def bullets(items):
        return "\n".join(f"- {x}" for x in items)

    return f"""**Stage:** {STAGES[issue['stage']]}

## Motivation

{issue['motivation']}

## Scope

{bullets(issue['scope'])}

## Implementation notes

{bullets(issue['notes'])}

## Acceptance criteria

{bullets(issue['acceptance'])}

## Tests required

{bullets(issue['tests'])}

## Dependencies

{dep_block}
"""


def write_files():
    BACKLOG.mkdir(parents=True, exist_ok=True)
    for f in BACKLOG.glob("*.md"):
        f.unlink()
    numbers = {}
    index = ["# Issue backlog\n",
             "Generated by `scripts/issues.py`. These mirror the GitHub issues; "
             "regenerate after editing the script.\n"]
    by_stage = {}
    for issue in ISSUES:
        by_stage.setdefault(issue["stage"], []).append(issue)
    for stage in sorted(by_stage):
        index.append(f"\n## {STAGES[stage]}\n")
        for issue in by_stage[stage]:
            name = slug(issue) + ".md"
            (BACKLOG / name).write_text(
                f"# {issue['key']} — {issue['title']}\n\n" + body(issue, numbers))
            index.append(f"- [{issue['key']} — {issue['title']}]({name})")
    (BACKLOG / "README.md").write_text("\n".join(index) + "\n")
    print(f"wrote {len(ISSUES)} issue files to {BACKLOG}")


def sh(args, dry):
    print("+", " ".join(args))
    if dry:
        return ""
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def create(dry):
    # labels
    for stage, name in STAGES.items():
        sh(["gh", "label", "create", f"stage-{stage}", "--force",
            "--description", name, "--color", "1d76db"], dry)
    # milestones (via API; ignore if they exist)
    existing = ""
    if not dry:
        existing = subprocess.run(
            ["gh", "api", "repos/{owner}/{repo}/milestones?state=all", "--jq", ".[].title"],
            capture_output=True, text=True).stdout
    for stage, name in STAGES.items():
        if name in existing:
            continue
        sh(["gh", "api", "repos/{owner}/{repo}/milestones", "-f", f"title={name}"], dry)

    numbers = {}
    for issue in ISSUES:
        b = body(issue, numbers)
        args = ["gh", "issue", "create",
                "--title", f"{issue['key']} — {issue['title']}",
                "--body", b,
                "--label", f"stage-{issue['stage']}",
                "--milestone", STAGES[issue["stage"]]]
        out = sh(args, dry)
        m = re.search(r"/issues/(\d+)", out)
        if m:
            numbers[issue["key"]] = int(m.group(1))
        elif not dry:
            print(f"!! could not parse issue number from: {out}", file=sys.stderr)
    # second pass: rewrite bodies so dependency links carry the real numbers
    for issue in ISSUES:
        n = numbers.get(issue["key"])
        if not n or not issue["deps"]:
            continue
        sh(["gh", "issue", "edit", str(n), "--body", body(issue, numbers)], dry)
    print(f"created/updated {len(numbers)} issues")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--create", action="store_true", help="create GitHub issues too")
    ap.add_argument("--dry-run", action="store_true", help="print gh commands, don't run")
    a = ap.parse_args()
    write_files()
    if a.create:
        create(a.dry_run)
