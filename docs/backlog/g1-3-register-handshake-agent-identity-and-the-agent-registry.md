# G1.3 — REGISTER handshake, Agent identity, and the Agent registry

**Stage:** Stage 1 — Basic Agent connection

## Motivation

The Controller must know which Agent is on which connection.

## Scope

- Define a minimal REGISTER exchange: Agent sends its `agent-id` and protocol version right after connecting; Controller replies OK or REJECTED.
- Add an in-memory `AgentRegistry` on the Controller: `agentId -> AgentConnection`.
- Register on REGISTER_OK; deregister on disconnect.

## Implementation notes

- This is a provisional encoding; Stage 3 formalises it. Keep it a single small framed message so Stage 3 can slot in.
- Reject unknown protocol versions.

## Acceptance criteria

- After connect+register, `AgentRegistry.get("alpha")` returns the live connection.
- After disconnect, it returns empty.

## Tests required

- Controller test: register two Agents, assert both are tracked; drop one, assert only it is removed.

## Dependencies

- G1.1
- G1.2
