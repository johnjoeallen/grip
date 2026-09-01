# G4.1 — Controller: channel allocation and channel→exchange map

**Stage:** Stage 4 — Multiplexing

## Motivation

Many concurrent external requests over one Agent connection.

## Scope

- Allocate a unique `ChannelId` per external request on a connection.
- Maintain `channel -> external exchange` with its lifecycle state.
- Reclaim ids after the channel closes; enforce `MAX_CHANNELS_PER_CONNECTION`.
- Remove the Stage 2 single-channel restriction on the Controller.

## Implementation notes

- Monotonic allocation with a free set for reuse is fine.
- Guard the map with explicit locking or a concurrent map + per-channel state object.

## Acceptance criteria

- 100 concurrent requests to one Agent get 100 distinct channels.
- Exceeding the cap yields a 503, not a crash.

## Tests required

- Controller test: concurrent allocation is unique and thread-safe; cap is enforced; ids are reused after close.

## Dependencies

- G3.5
