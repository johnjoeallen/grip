# G4.2 — Agent: channel→internal-exchange map

**Stage:** Stage 4 — Multiplexing

## Motivation

The Agent side of multiplexing.

## Scope

- Maintain `channel -> internal request/response` state.
- Each channel's internal call runs on its own virtual thread.
- Remove the Stage 2 single-channel restriction on the Agent.

## Implementation notes

- Cancellation must reach the right virtual thread (interrupt / request abort).
- No shared mutable buffers between channels.

## Acceptance criteria

- Multiple channels proxy concurrently through one Agent.
- Cancelling one channel does not disturb the others.

## Tests required

- Agent test: interleaved frames for 3 channels produce 3 correct, independent internal calls.

## Dependencies

- G3.5
