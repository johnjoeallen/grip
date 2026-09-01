# G3.1 — Design and document the GRIP wire format

**Stage:** Stage 3 — GRIP framing protocol

## Motivation

Channels and streaming need a real frame format, not the Stage 2 placeholder.

## Scope

- Specify a binary, length-prefixed frame: type tag, channel id, flags, payload length, payload.
- Specify how `REQUEST_START`/`RESPONSE_START` carry method/path/status and headers.
- Specify REGISTER, PING/PONG, CANCEL, ERROR (with an error-code enum).
- Write it all into `docs/protocol.md`, moving items from 'deferred' to 'decided'.

## Implementation notes

- Keep it minimal — no settings negotiation yet unless clearly needed.
- Must be codec-able over a plain HTTP/1.1 stream; no HTTP/2-only assumptions.

## Acceptance criteria

- `docs/protocol.md` fully describes every frame currently in `FrameType` plus REGISTER, with byte layouts.
- The design is reviewed and agreed on the issue before coding G3.2.

## Tests required

- n/a (design doc).

## Dependencies

- G2.3
