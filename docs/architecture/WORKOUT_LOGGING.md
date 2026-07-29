# Workout Logging Architecture

Project: MyFitnessLog
Scope: the active workout-logging experience — start a session, log sets, rest,
manage exercises, and finish — on the Android client.

## 1. Overview

Workout logging is the core interaction of MyFitnessLog: the screen a user is on
while training. It turns a plan (or an empty session) into a permanent record of what
was actually performed, entirely offline.

This document is the canonical reference for how that subsystem is built. It
complements the system-wide [ARCHITECTURE.md](../ARCHITECTURE.md) and the
client-wide [ANDROID_ARCHITECTURE.md](../ANDROID_ARCHITECTURE.md); where those
describe the whole app, this describes the workout-logging domain in depth — its
state models, contracts, read models, and runtime consumers.

The design rests on one organising idea:

> **History stores facts, not intentions.** A set becomes part of history *only* when
> the user marks it complete. Everything before that — planned rows, half-typed
> values — is transient interface state that never reaches the database.

Everything else follows from that: which rows persist, when the rest timer starts,
what "previous performance" means, and why a completed workout can never change.

The feature is organised in layers, each with a single responsibility:

```
        Android UI (Jetpack Compose)        renders state, emits intents; holds no rules
              │  intents            ▲ immutable state
              ▼                     │
        ViewModels (orchestration)        merge state, call the domain, dispatch mutations
              │  calls              ▲
              ▼                     │
        Domain (SetTransitions, RestEffect)   the single source of business rules
              │  SetMutation        │
              ▼                     │
        Repositories (data boundary)      persistence, validation, tombstones, sync status
              │                     │
              ▼                     │
        Room (local source of truth)      entities, observed via Kotlin Flows
              │
              ▼
        Backend sync (one-way, offline-first)
```

Four features sit *beside* the domain rather than inside it — the rest timer,
previous-performance, the exercise ordering controls, and the persistent workout
indicator. Each either reads a projection or reacts to a domain event; none of them
contains logging rules. This separation is what lets the system grow (a watch app, a
notification, an analytics feed) without touching the state machine.

The workout-logging domain is a pure Kotlin, offline-first feature. It introduces no
database migration, no REST endpoint, and no change to the synchronization protocol:
it composes existing persistence and sync mechanisms behind new domain rules.

## 2. Architectural Principles

- **The domain is the source of truth for rules.** Every completion, edit, undo, and
  delete *decision* originates in one place — the set state-machine — and nowhere
  else.
- **The UI owns no business rules.** Compose renders immutable state and emits
  intents. It holds only ephemeral interface state (the text in a field, whether a
  sheet is open).
- **ViewModels orchestrate.** They merge persisted and transient state, call the
  domain, dispatch the resulting persistence actions to repositories, forward domain
  events to consumers, and expose immutable UI state. They contain no rules and never
  touch Room or sync directly.
- **Repositories persist.** They own Room writes, value validation, tombstones, and
  sync-status bookkeeping.
- **History stores facts.** Only completed sets persist. Intent (planned/partial rows)
  is transient and never written.
- **Read models never mutate.** Previous-performance and the workout indicator are
  projections over existing data; observing them is side-effect free.
- **Consumers react; they never feed back.** The rest timer reacts to a domain event;
  it does not drive completion.
- **A routine is a template; a session is one execution of it.** Actions taken during
  a workout change the session snapshot, never the routine
  (see [ADR-0004](../ADR/0004-snapshot-based-workout-history.md)).

These principles are enforced by the invariants summarised in §10.

## 3. Workout Session Lifecycle

A `WorkoutSession` is the top-level container: `WorkoutSession → WorkoutExercise →
WorkoutSet`. Its lifecycle is deliberately small.

### States

| State | Persisted | Meaning |
|---|---|---|
| **NOT_STARTED** | No (conceptual) | No session row exists; the Workout tab shows "No active workout". This is the *absence* of a session, not a stored status. |
| **IN_PROGRESS** | Yes | An active, mutable session. Sets can be logged and edited; the rest timer can run. **At most one exists at any time.** |
| **COMPLETED** | Yes (terminal) | Finished and **immutable** — the permanent record of the workout. Counts toward history and analytics. |
| **DISCARDED** | Yes (terminal) | Abandoned. **Retained, not deleted**, but hidden from the user and excluded from history and analytics. |

There is no `PAUSED` state. Elapsed time is derived from `startedAt` (frozen at
`endedAt`), so there is no paused clock to model.

### Transitions

```
        (no session)
        NOT_STARTED
             │  start from routine / start empty
             ▼
        ┌───────────────┐
        │  IN_PROGRESS  │◀── resume after app restart / re-entry
        └──┬─────────┬──┘
   Finish  │         │  Discard
           ▼         ▼
       COMPLETED   DISCARDED     ← terminal; no outgoing transitions
```

| From | To | Trigger | Effect |
|---|---|---|---|
| NOT_STARTED | IN_PROGRESS | Start routine / Start empty | Creates the session and (for a routine) its exercise snapshots atomically; sets `startedAt`; uploads promptly. |
| IN_PROGRESS | IN_PROGRESS | Resume | Returns the existing session; nothing new is created. |
| IN_PROGRESS | COMPLETED | Finish | Records `endedAt`; seals completed sets immutable; discards transient rows; cancels the rest timer. |
| IN_PROGRESS | DISCARDED | Discard | Records `endedAt`; nothing becomes history; cancels the rest timer. |

Terminal states never reopen and never convert into one another. Any mutation of a
terminal session (add/edit/delete a set, reorder or remove an exercise) is rejected
on the device (`requireInProgress`) and by the backend (409). Starting a workout
while one is already active resumes it rather than creating a second — the "single
active session" rule that the resume affordance, the rest timer, and the persistent
indicator all depend on.

### Durability and recovery

The session is durable because it is written at creation and uploaded promptly; only
*transient interface state* is lost across process death.

- **App restart or re-entry:** the Workout tab observes the active session; if one
  exists it is resumed and re-rendered. Completed sets reload from Room; planned rows
  are re-synthesised from the exercise's target set count (they were never
  persisted); previous-performance is re-queried.
- **Process death mid-workout:** identical recovery — no loss of completed sets. The
  rest countdown is lost (it is transient by design). The elapsed clock reconstructs
  exactly, because it derives from the persisted `startedAt`.
- **Crash before the start transaction commits:** the atomic transaction guarantees
  no half-created, exercise-less session is left behind.

## 4. Set Logging State Machine

The set state machine is the heart of the feature. It is a set of pure functions in
`feature/workout/domain/logging/` with no Android, Room, or coroutine dependencies,
which makes it exhaustively unit-testable and keeps the rules in exactly one place.

### Row states

A row in the inline set table is in one of five logical states. Only the last three
persist as `WorkoutSet` rows; the first two are transient interface state.

| State | Persisted | Rest timer | In history |
|---|---|---|---|
| **PLANNED** (empty) | No | — | No |
| **PARTIALLY_FILLED** | No | — | No |
| **COMPLETED** | Yes (`isCompleted = true`) | **starts** on entry | Yes |
| **EDITED_AFTER_COMPLETION** | Yes (`isCompleted = true`) | **does not restart** | Yes (re-uploaded) |
| **UNCOMPLETED** (reverted) | reverts to transient intent | **stops** | No |

```
     start routine / Add Set
              │
              ▼
        ┌─────────┐  type    ┌──────────────────┐
        │ PLANNED │─────────▶│ PARTIALLY_FILLED │
        │ (empty) │◀─────────│                  │
        └────┬────┘  clear   └────────┬─────────┘
             │   complete (event)  ───┤
             ▼                        ▼
          COMPLETED  (persist the set; start rest once)
             │  edit          │  undo           │  delete
             ▼                ▼                 ▼
   EDITED_AFTER_COMPLETION  UNCOMPLETED     tombstoned
   (values change only)     (back to intent)  (ADR-0007)
```

### The transitions

`SetTransitions` exposes four pure functions. Each takes the current row plus the
entered values and returns a result carrying two things: a **persistence action**
(`SetMutation`) for the repository to perform, and a **rest event** (`RestEffect`,
§5) for the timer to consume.

| Transition | Condition | Persistence action | Rest event |
|---|---|---|---|
| `complete` | transient row with weight + reps | `Insert` (write the set) | `START` |
| `complete` | already completed, or not fillable | `None` | `NONE` |
| `edit` | completed row | `Update` (values only) | `NONE` |
| `edit` | transient row | `None` (value change stays in memory) | `NONE` |
| `undo` | completed row | `DeleteWithTombstone` | `STOP` |
| `undo` | transient row | `None` | `NONE` |
| `delete` | completed row | `DeleteWithTombstone` | `NONE` |
| `delete` | transient row | `RemoveTransientRow` | `NONE` |

The ViewModel dispatches the returned `SetMutation` to the repository; it never
decides which mutation applies. This ownership split — the domain decides *what*
happens, the ViewModel wires it up, the repository persists it, Compose shows it — is
the reason the rules cannot drift into the UI.

### Why "completing" is special

The single organising rule is that **completion is an event; editing is not.** The
incomplete→complete transition is the *only* thing that writes a set, turns the row
green, and starts the rest countdown. Editing an already-completed set changes its
stored values and re-uploads them, but never restarts rest, never re-fires
completion, and never changes the row's colour. `COMPLETED` and
`EDITED_AFTER_COMPLETION` are identical in storage (`isCompleted = true`); they differ
only in whether side effects fire, and that difference lives in the transition, not
in a database column.

This is what makes the flow usable: fixing a typo in a logged set does not re-trigger
rest or double-count the set in analytics.

### Completion inputs

- **Inline editing.** Weight and reps are edited directly in the row (a
  spreadsheet-style model, no modal). Values commit when the field loses focus.
- **One-tap completion.** Tapping the ✓ on a filled row completes it immediately. RPE
  is optional and never gates completion.
- **RPE bottom sheet.** Tapping the RPE cell opens a sheet with the set context, an
  effort label and description, and a pill selector over the canonical scale
  `6, 7, 7.5, 8, 8.5, 9, 9.5, 10`. Its Done action saves the RPE *and* completes the
  set in one intent. There are therefore two completion entry points (the ✓ and the
  sheet's Done) but one underlying transition. The scale bounds the picker only; the
  repository's numeric validation (RPE within 1–10) remains authoritative, so
  historical values outside the scale still render.
- **Added sets behave identically to planned sets.** A set added with "Add Set" runs
  through the same states, transition, timer, colour, and sync as a planned row. The
  only difference is provenance (it did not come from the routine snapshot); there is
  no separate code path.

### Row identity and ordering

Completed rows are keyed `set:<id>` (their persisted set id); transient rows are keyed
`draft:<uuid>`. Each row also carries a stable *ordering slot* — a completed row's
slot is its persisted set number, a transient row's slot is the order it was seeded
or added in. `WorkoutRowMerger` interleaves completed and planned rows by slot and
renumbers them `1..n` for display.

Ordering by slot (rather than "completed rows always first") is what lets **undo
restore a set into its original position** instead of appending it to the bottom:
undo reverts the completed set to a transient row that reuses the vacated slot, so it
falls back exactly where it was.

## 5. RestEffect Event Model

`RestEffect` is a **domain event**, not a timer implementation detail. It is the
narrow, deterministic signal by which the set state machine tells downstream
consumers that a rest period should begin or end. Modelling rest as an event — rather
than calling the timer from inside the transition — is what allows new consumers
(a notification, a watch companion, an analytics feed) to subscribe without touching
the state machine.

### The event

`RestEffect` is a closed enum with three values, produced *only* by `SetTransitions`,
exactly one per transition:

| Value | Meaning | Emitted by |
|---|---|---|
| `START` | A rest period should begin now. | `complete` that persists a set |
| `STOP` | A running rest period should end now. | `undo` of a completed set |
| `NONE` | No rest change. | edits, deletes, and no-ops |

`START` corresponds one-to-one with the incomplete→complete edge; `STOP` with the
complete→uncompleted edge. Nothing else moves the timer. The value is a pure function
of the transition and the row's state — deterministic, with no clock, randomness, or
I/O — and producing it changes nothing, because it is simply data on the returned
result. Consumers cause effects; the producer does not.

### The rest timer (the current consumer)

The rest timer is the one consumer today. It is a single transient countdown that
belongs to the **session**, not to an exercise — a lifter rests once at a time, so
there is exactly one countdown, shown in the docked bar (`−15 · MM:SS · +15 · Skip`).
It reacts to the event: `START` starts the countdown using the completing exercise's
rest duration; `STOP` cancels it; `NONE` does nothing. Finishing or discarding the
workout cancels it. It holds no business rules — remove it and completion logic is
untouched.

- **Duration is per-exercise; the countdown is one.** Each exercise carries a rest
  duration (`targetRestSeconds`), snapshotted from the routine and editable during
  the workout through a wheel picker whose options run *Off, then 5-second steps up to
  2 minutes, then 15-second steps up to 5 minutes*. Editing it changes the session
  snapshot only, never the routine. Completing a set starts the single countdown with
  the completing exercise's duration, superseding any prior countdown.
- **Transient, but it survives navigation.** The countdown is never persisted. Its
  controller is scoped to the application (not to the workout screen's ViewModel), so
  it keeps running while the user visits other tabs and shows the correct remaining
  time on return. It is lost only on process death — acceptable, as there is no
  foreground service or alarm in scope.

## 6. Previous Performance Projection

The **PREVIOUS** column shows what was done last time for each set, so the user can
compare and progressively overload. It is a **read-only projection over completed
history** — computed on demand, never stored, and completely independent of workout
execution, persistence, and sync.

### Lookup rules

Given an exercise, the projection returns the sets from the **single most recent
completed workout** that contained it:

- **Completed workouts only.** In-progress (including today's) and discarded sessions
  are excluded, so today's workout never matches itself.
- **Cross-routine, by exercise.** The match is on the master exercise, regardless of
  which routine produced the workout — progressive overload accrues on the *movement*,
  and the same lift may appear in many routines.
- **Latest by start time.** Among completed workouts containing the exercise, the most
  recent one is used.
- **Exact set-number matching.** Today's set N maps to that workout's set N. If the
  previous workout had no set N, the cell shows `-`. A value is never shifted from an
  adjacent set into a different number — fatigue makes sets non-interchangeable, so a
  shifted comparison would mislead.

The projection is a single indexed read over the immutable history tables. It touches
no other subsystem, and displaying it can never mutate history. It also feeds an
optional prefill of the weight/reps fields, but the *displayed* previous value is
governed solely by the rules above.

This same read model is the intended foundation for future read-only consumers
(progress graphs, personal-record detection, analytics): each would add an additive
read-only query rather than changing the producer.

## 7. Exercise Ordering

Exercise ordering inside a workout is **session-local execution state**: the order the
lifter chooses to perform exercises today. The user can move an exercise up or down,
or remove one (a machine is busy, they prefer a different order, they added one by
mistake) without editing the routine.

- **Source of truth.** `WorkoutExercise.exerciseOrder` is the sole ordering key. It is
  seeded from the routine's order at workout start and thereafter changed only by
  session-scoped actions. Exercises render sorted by `exerciseOrder`; a move swaps two
  rows' order values.
- **The routine is never touched.** Reordering or removing writes only the session's
  `WorkoutExercise` rows; the routine template and its exercise order are left
  unchanged (see [ADR-0004](../ADR/0004-snapshot-based-workout-history.md)).
- **History is never touched.** Previous-performance matches by exercise and set
  number, not by order, so reordering never changes what PREVIOUS shows.
- **Removal propagates deletions.** Removing an exercise tombstones its completed sets
  so those deletions converge on the backend
  (see [ADR-0007](../ADR/0007-workout-set-deletion-tombstones.md)). Propagation of the
  now-empty exercise *row* itself is a known limitation: on an in-progress (not yet
  completed) session an already-uploaded exercise row can linger on the backend as an
  empty exercise. This is a cosmetic divergence, not data loss.

Grouping concepts such as supersets are deliberately **not** part of ordering; they
would be a separate domain model, not an extension of this one.

## 8. Workout Persistence Indicator

The persistent workout indicator is the pill shown on the other top-level screens
(Home, History, Profile) while a workout is active. "Persistent" refers to the
*workout*: the active session already exists as a durable row (created and uploaded at
start), so the user can navigate away and back without losing it — the indicator
simply surfaces that fact.

It is a **derived, read-only projection** over the single active session. It reads
only the active-session observation and derives its display:

- a live indicator dot,
- the routine name (or "Workout" for an ad-hoc session with no routine),
- the elapsed time (derived from `startedAt`),
- the current exercise — the first exercise, in order, whose completed-set count is
  still below its target; once all targets are met, the last exercise.

Because at most one session is ever active, the indicator is unambiguous and needs no
logic to choose between workouts. It appears exactly when an active session exists and
disappears when the session becomes terminal.

The indicator exposes two actions and no persistence logic of its own:

- **Resume** — tapping the pill navigates to the active workout.
- **Discard** — a bin icon opens the same confirmation dialog as the workout screen
  and, on confirmation, calls the repository's existing `discardWorkout` on the active
  session. This is the only mutation the indicator can trigger, and only on deliberate,
  confirmed user intent; it invents no persistence path of its own.

## 9. Relationship to Synchronization

Workout logging is offline-first and reuses the app's existing synchronization
mechanism unchanged — it adds no endpoint, schema, or protocol change. The full
strategy is in [SYNC.md](../SYNC.md); the points that matter here:

- **The UI never awaits the network.** Every action writes to Room and returns; the
  screen observes Room via Flows. Uploads happen in the background.
- **One-way, idempotent.** Local writes upload Android → backend as UUID-keyed upserts,
  so replays are safe. The backend is the permanent source of truth
  ([ADR-0003](../ADR/0003-backend-is-system-source-of-truth.md)); Room is the local
  source of truth ([ADR-0002](../ADR/0002-room-is-android-source-of-truth.md)).
- **The session is uploaded at start** so its identity exists on the backend from the
  first moment, and again on the terminal transition (the highest-value event, which
  seals the workout).
- **Deletions propagate as tombstones.** Because the client hard-deletes, undoing or
  deleting a *completed* set — and removing an exercise with completed sets — records a
  tombstone so the backend converges rather than resurrecting the row
  ([ADR-0007](../ADR/0007-workout-set-deletion-tombstones.md)).
- **Transient rows never sync.** Planned and partially-filled rows are never written,
  so they never enter the sync pipeline at all.

## 10. System Invariants (summary)

These properties always hold across workout logging. A feature that would violate one
is wrong by construction; together they are the acceptance criteria for any change to
this subsystem.

| # | Invariant |
|---|---|
| 1 | **Exactly one active session.** At most one workout is IN_PROGRESS; starting another resumes it. |
| 2 | **A completed workout is immutable.** Its session, exercises, and sets are frozen. |
| 3 | **Terminal states are permanent.** COMPLETED and DISCARDED never reopen or convert. |
| 4 | **Planned and partial rows are transient.** They never reach Room, sync, or history. |
| 5 | **Only completed sets become history.** Everything else vanishes on finish. |
| 6 | **Completion is an event; editing is not.** Only incomplete→complete persists a set, colours it, and starts rest. |
| 7 | **The rest timer is session-scoped and transient.** One countdown, never persisted; only Complete/Undo/Skip/Finish/Discard affect it. |
| 8 | **A routine is a template; a session is one execution.** Session actions never mutate the routine. |
| 9 | **Reordering never modifies the routine.** It writes only `WorkoutExercise.exerciseOrder`. |
| 10 | **Progressive overload follows the exercise, not the routine.** PREVIOUS matches the exercise across routines. |
| 11 | **PREVIOUS is read-only.** Displaying prior performance never writes history. |
| 12 | **Set-number matching is exact.** Today's set N maps to previous set N, or shows `-`. |
| 13 | **Deletions of persisted data propagate via tombstones.** So the backend converges. |
| 14 | **Elapsed time is derived, never stored.** It is computed from `startedAt`. |

## 11. Extension Guidelines

The layering exists so the system can grow without destabilising the rules. When
adding to workout logging:

- **A new runtime consumer** (a notification, a watch companion, rest analytics):
  subscribe to the `RestEffect` the ViewModel already computes. Do not modify the state
  machine.
- **A new read model** (personal records, progress graphs, insights): add an additive,
  read-only DAO query and repository method, and format it in the ViewModel — following
  the previous-performance pattern. Never write from it.
- **New UI:** render existing state and emit intents. If a composable finds itself
  deciding *when a set completes* or *what persists*, that logic belongs in the domain.
- **A genuinely new domain concept** (for example, supersets): design it as its own
  model with its own rules and, where needed, its own schema — keeping the existing
  state machine intact.

Anti-patterns to avoid: deciding completion/undo/validity inside Compose;
re-implementing a transition in the ViewModel instead of calling `SetTransitions`;
persisting a transient row; storing `isCompleted = false` on undo instead of
tombstoning and reverting; giving a read model a write path; calling Room or sync from
the ViewModel or Compose; mutating the routine from a session action; or restarting
the rest timer on an edit.

## 12. References

- [ARCHITECTURE.md](../ARCHITECTURE.md) — system-wide architecture across all three
  apps.
- [ANDROID_ARCHITECTURE.md](../ANDROID_ARCHITECTURE.md) — the Android client's overall
  architecture and conventions.
- [DATABASE.md](../DATABASE.md) — the relational model behind
  `WorkoutSession → WorkoutExercise → WorkoutSet`.
- [SYNC.md](../SYNC.md) — the offline-first synchronization strategy.
- [ADR-0001](../ADR/0001-history-is-source-of-truth.md) — history is the source of
  truth.
- [ADR-0002](../ADR/0002-room-is-android-source-of-truth.md) — Room is the Android
  source of truth.
- [ADR-0003](../ADR/0003-backend-is-system-source-of-truth.md) — the backend is the
  system source of truth.
- [ADR-0004](../ADR/0004-snapshot-based-workout-history.md) — snapshot-based workout
  history (why a session is an immutable copy of the routine).
- [ADR-0007](../ADR/0007-workout-set-deletion-tombstones.md) — tombstones for workout
  set deletions.
- [ADR-0008](../ADR/0008-event-driven-set-logging.md) — event-driven set logging:
  transient intent, persisted on completion (§4).
- [ADR-0009](../ADR/0009-rest-as-a-domain-event.md) — rest is a domain event consumed
  by a session-scoped timer (§5).
- [ADR-0010](../ADR/0010-session-execution-vs-routine-templates.md) — session-scoped
  execution never mutates routine templates (§7).
- [ADR-0011](../ADR/0011-read-only-workout-projections.md) — workout state is surfaced
  through read-only projections (§6, §8).
