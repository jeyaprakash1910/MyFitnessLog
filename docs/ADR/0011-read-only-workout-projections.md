# ADR-0011 — Workout state is surfaced through read-only projections

Date: 2026-07-28
Status: Accepted
Related: ADR-0001 (workout history is the source of truth), ADR-0004 (snapshot-based
workout history), docs/architecture/WORKOUT_LOGGING.md, SYNC.md

## Context

Two features display information derived from workout data. The **PREVIOUS** column
shows what was done for each set last time, so the user can progressively overload. The
**persistent workout indicator** is the pill shown on other screens while a workout is
active, letting the user jump back to it.

Both read history or live state and render it. The risk is that a display feature
acquires a write path — rendering "last time's" numbers could alter them, or an
indicator could accumulate persistence logic of its own — which would corrupt the
immutable history ADR-0001 protects.

## Decision

Model these features as **read-only projections** over existing data. A projection
derives its display and never writes.

- **Previous Performance** is a read-only query: the most recent *completed* workout
  containing the exercise, regardless of routine, matched by **exact set number**,
  showing `-` when there is no matching set. Progression follows the movement, not the
  template.
- **The workout indicator** is a derived view of the single active session: it shows
  the routine name, elapsed time (derived from the start timestamp), and the current
  exercise, and it is visible exactly when an active session exists.
- **Observing either projection is side-effect free** — no write, no sync request, no
  state change.
- The **one deliberate exception** is a single explicit, user-confirmed action on the
  indicator — discarding the active workout — which delegates to the repository's
  existing discard operation rather than inventing persistence of its own. It
  supersedes the earlier position that the indicator carried navigation only.

Future derived features (progress graphs, personal-record detection, analytics) follow
the same pattern: an additive, read-only query, formatted for display, with no write
path.

## Alternatives considered

**Compute previous performance from the current routine's last use.** Rejected: the
same exercise appears in many routines, so tying progression to the routine hides
history and defeats progressive overload.

**Let projections cache or write derived state.** Rejected: any write path from a
display feature can corrupt immutable history (ADR-0001). Recomputing on demand from
the source of truth is simpler and safe.

**Give the indicator broader control of the session.** Rejected: it derives from the
single active session and needs no state of its own. The sole mutation it exposes is an
explicit, confirmed discard that reuses the existing repository operation.

## Consequences

Positive:

- Displaying history can never corrupt it.
- New read models are additive and cheap; the pattern scales to future analytics
  without touching the domain or the state machine.

Negative / accepted:

- Projections recompute on demand rather than caching — acceptable, as the queries are
  indexed and small.
- The indicator's single confirmed discard is a deliberate, narrowly-scoped exception
  to "read-only," justified by being an explicit user action that delegates to the
  existing path.

Related public documentation: docs/architecture/WORKOUT_LOGGING.md §6 (previous
performance), §8 (workout indicator).
