# ADR-0010 — Session-scoped execution never mutates routine templates

Date: 2026-07-28
Status: Accepted
Related: ADR-0004 (snapshot-based workout history), ADR-0001 (workout history is the
source of truth), docs/architecture/WORKOUT_LOGGING.md, DATABASE.md

## Context

ADR-0004 establishes that starting a workout copies the routine into an immutable
snapshot, so later routine edits do not rewrite past workouts. But a workout is also
edited *while it runs*: the user reorders exercises when a machine is busy, removes one
added by mistake, changes an exercise's rest duration, and edits set values.

The question this decision settles is which direction those in-session edits flow —
into the session, or back into the routine template.

## Decision

Every action taken while executing a workout writes **only the session snapshot**;
none of them modifies the routine template.

- **Exercise order is session-local.** It lives on `WorkoutExercise.exerciseOrder`,
  seeded from the routine at start and thereafter changed only by in-session moves.
  Exercises render sorted by this value; a move swaps two rows' order values.
- **Removing an exercise, editing a set, and changing an exercise's rest duration** all
  target the session's `WorkoutExercise` / `WorkoutSet` rows. The `Routine` and
  `RoutineExercise` tables are never written by executing a workout.
- **The routine changes only when the user edits the routine directly**, elsewhere in
  the app.

## Alternatives considered

**Let in-session edits update the routine.** Rejected: execution changes constantly —
fatigue, occupied equipment, skipped sets — and folding that noise back into the
template would make routines unreliable, silently rewriting a plan every time it is
performed.

**Derive session order from the routine at render time.** Rejected: it cannot represent
a today-only reorder without either mutating the routine or inventing a parallel
per-session overlay — which is exactly what `exerciseOrder` on the snapshot already is.

## Consequences

Positive:

- Routines stay stable and reusable; a messy session never corrupts the plan.
- Ordering and previous-performance stay decoupled: previous-performance matches by
  exercise and set number, not by order, so reordering never changes what it shows.

Negative / accepted:

- Planning data is duplicated onto the session — an overhead already accepted by
  ADR-0004 for historical accuracy.
- Removing an exercise tombstones its completed sets so those deletions converge, but
  propagation of the now-empty exercise *row* itself is a known, bounded limitation on
  an in-progress session: a cosmetic divergence, not data loss.

Related public documentation: ADR-0004; docs/architecture/WORKOUT_LOGGING.md §7
(exercise ordering).
