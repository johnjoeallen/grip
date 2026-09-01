# G3.3 — Controller: run the connection on the real codec

**Stage:** Stage 3 — GRIP framing protocol

## Motivation

Replace the Stage 2 placeholder on the Controller side.

## Scope

- Read/write `Frame`s via `FrameCodec` on the Agent connection.
- REGISTER, PING/PONG, and the single-channel request/response path all go through frames now.
- A malformed frame closes the connection with a logged protocol error.

## Implementation notes

- Keep the single-channel restriction if G2.2 took it; G4 removes it.
- One reader loop per connection; writes still serialised.

## Acceptance criteria

- Stage 2's e2e tests still pass, now over real frames.
- Feeding garbage bytes closes the connection cleanly.

## Tests required

- Controller test: malformed frame → connection closed, registry updated.

## Dependencies

- G3.2
