# G3.2 — grip-protocol: frame types and codec

**Stage:** Stage 3 — GRIP framing protocol

## Motivation

A tested encoder/decoder both services share.

## Scope

- Add `Frame` types (sealed interface + records per kind) in `dev.grip.protocol`.
- Add `FrameCodec` — encode to bytes, decode from a byte stream, incremental (handles partial reads).
- Enforce `MAX_FRAME_PAYLOAD_BYTES` / `MAX_HEADER_BYTES`; reject over-limit or malformed frames with a typed exception.

## Implementation notes

- No Spring, no I/O framework — operate on `ByteBuffer` / `byte[]` / `InputStream`.
- Zero-copy where reasonable for payloads.

## Acceptance criteria

- Every frame kind round-trips: encode → decode → equal.
- Truncated and oversized inputs raise the codec's exception, not a generic one.

## Tests required

- `grip-protocol` codec tests: round-trip table for all frames; fuzz/boundary tests for truncation, zero-length, max-length, bad type tag.

## Dependencies

- G3.1
