# ADR-0008 — Event-driven set logging: transient intent, persisted on completion

Date: 2026-07-28
Status: Accepted
Related: ADR-0001 (workout history is the source of truth), ADR-0004 (snapshot-based
workout history), ADR-0007 (workout set deletions propagate via tombstones),
docs/architecture/WORKOUT_LOGGING.md, DATABASE.md

## Context

While logging a workout, the set table shows planned and partially-entered rows
alongside completed ones. History, however, must contain only performed work
(ADR-0001): a planned set the user never did, or a half-typed value, is an intention,
not a fact. The system needs one unambiguous rule for when a set becomes part of the
permanent record, and it must keep intentions out of Room, synchronization, and
analytics.

Two related problems fall under the same rule. Fixing a typo in an already-logged set
must not behave like logging a new set — it must not restart the rest timer or
double-count the set. And undoing a completed set must not leave a "did-not-happen"
row behind in history.

## Decision

Model set logging as an event over a small, pure state machine.

- A set row is **transient interface state** (planned or partially filled) until the
  user completes it. Transient rows are never written to Room, sync, or history.
- The incomplete→complete transition is a **domain event**. It is the *only* thing
  that persists a `WorkoutSet`, marks the row completed, and starts the rest timer.
- **Editing a completed set is a pure value update.** It changes stored values (and
  re-uploads them) but never re-fires completion or rest, and never changes the row's
  completed state.
- **Undoing a completed set** deletes the persisted set, records a tombstone (ADR-0007)
  so the deletion converges on the backend, and reverts the row to transient intent.
  It never stores an `isCompleted = false` "ghost" row.
- These rules live in a **pure state machine** with no Android, Room, or coroutine
  dependencies. Each transition returns a persistence action for the repository to
  perform; the UI and ViewModel never decide what persists.

Because intent stays transient and the schema already carries the needed columns
(`isCompleted`, `rpe`, `exerciseOrder`) and a set-deletion tombstone, this model
requires **no database migration, REST endpoint, or synchronization-protocol change**.

## Alternatives considered

**Persist placeholder rows.** Write every planned row as a `0×0`,
`isCompleted = false` set and flip the flag on completion. Rejected: it pollutes
immutable history, uploads intentions to the backend, corrupts volume and
personal-record math, and generates needless tombstones whenever a plan is abandoned.

**Store undo as `isCompleted = false`.** Rejected: a reverted set never happened.
Leaving a flagged row keeps a non-fact in history and forces every read to exclude it,
so a query that forgets the filter silently shows work that was not done.

**Treat every set write identically (no distinct completion event).** Rejected:
without a precise completion edge, editing a completed set would restart rest and
re-log the set — making the flow unusable and double-counting analytics.

## Consequences

Positive:

- History contains only performed facts, upholding ADR-0001.
- The logging rules live in one place and are exhaustively unit-testable in isolation.
- Automatic rest and "edit is not completion" fall out of the model rather than being
  special-cased.
- The redesign composes existing persistence and sync with no schema, API, or protocol
  change.

Negative / accepted:

- The ViewModel synthesises and holds transient rows. They are lost on process death
  and re-synthesised on return, which is acceptable (see the session lifecycle in
  docs/architecture/WORKOUT_LOGGING.md §3).
- The distinction between "completed" and "edited after completion" lives in the
  transition, not in a database column, so it is preserved in code rather than read
  back from storage.

Related public documentation: docs/architecture/WORKOUT_LOGGING.md §4 (set state
machine), §5 (rest event), §10 (invariants).
