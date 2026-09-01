<!-- One issue, one PR. Keep main working. Stay in stage order. -->

Closes #<issue>

## What changed

<A short description of the change.>

## Checklist

- [ ] Implements exactly the scope of the linked issue — nothing from a later stage
- [ ] `mvn verify` passes locally
- [ ] Adds the tests named in the issue's **Tests required** section
- [ ] Docs updated where the issue implies it (especially `docs/protocol.md` for protocol changes)
- [ ] `grip-protocol` still has no Spring / HTTP-library / Controller / Agent imports
- [ ] No new infrastructure (broker, database, Redis, Kafka, Kubernetes, mandatory WebSockets)

## Deferred to a later issue

<Anything intentionally left out, with the issue/stage it belongs to. "Nothing" is a fine answer.>
