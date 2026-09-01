# Contributing to GRIP

GRIP is developed one small issue at a time, in stage order. Before starting,
read [`docs/roadmap.md`](docs/roadmap.md) and the issue you intend to pick up.

## Ground rules

- **One issue, one PR.** Keep changes small enough to review in one sitting.
- **Leave `main` working.** Every merge must build and pass `mvn verify`.
- **Respect the stage order.** Don't implement a later stage's behaviour to
  "get ahead" — it makes review harder and the roadmap meaningless.
- **`grip-protocol` stays pure.** No Spring, no HTTP library, no Controller or
  Agent imports.
- **No new infrastructure.** No broker, database, Redis, Kafka, Kubernetes, or
  mandatory WebSockets. If you think you need one, open a discussion first.

## Workflow

```bash
git checkout -b issue-<number>-<slug>
mvn verify
# ... implement, with tests ...
git commit
gh pr create --fill
```

## What each PR should include

- Code plus the tests named in the issue's **Tests required** section.
- Any doc updates the issue implies (especially `docs/protocol.md` for
  protocol changes).
- A note in the PR description of anything deferred to a later issue.

## Style

- Java 21. Records, sealed types, pattern matching where they fit.
- Virtual threads for request handling.
- Explicit, local synchronization. No reactive framework unless a streaming
  requirement forces it, and then only with discussion.
- Match the surrounding code's naming and comment density.
