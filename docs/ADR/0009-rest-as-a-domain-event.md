# ADR-0009 — Rest is a domain event consumed by a session-scoped timer

Date: 2026-07-28
Status: Accepted
Related: ADR-0008 (event-driven set logging), docs/architecture/WORKOUT_LOGGING.md

## Context

Completing a set should start a rest countdown. Calling a timer directly from the
completion code would couple the set state machine to one specific consumer and make
it hard to add others later — a notification, a watch companion, or rest analytics.

The app must also decide how many countdowns exist and what duration they use. A
lifter rests once at a time, but each exercise carries its own configured rest
duration.

## Decision

Model rest as a domain event, and make the timer a consumer of it.

- The set state machine emits a **rest lifecycle event** with three values — start,
  stop, or none — carried as data on the transition result. Completing a set emits
  *start*; undoing a completed set emits *stop*; edits, deletes, and no-ops emit
  *none*. The value is a pure function of the transition; producing it causes no side
  effect.
- **Exactly one transient rest countdown** exists during an active workout. It belongs
  to the **workout session**, not to any exercise. It consumes the event: *start*
  begins the countdown using the completing exercise's rest duration; *stop* cancels
  it. Finishing or discarding the workout cancels it.
- The countdown is **never persisted**. It survives navigating between screens (its
  lifetime is tied to the application session, not to the workout screen), and is lost
  only on process death — acceptable, as there is no foreground service or alarm in
  scope.
- The timer holds **no logging rules**. Removing it leaves completion logic untouched;
  adding another consumer means subscribing to the same event without modifying the
  state machine, the ViewModel, or the UI.

## Alternatives considered

**One timer per exercise.** Rejected: it models an impossible state — a lifter resting
on several exercises at once — and multiplies interrupting countdowns.

**Call the timer directly from the completion transition.** Rejected: it couples the
domain to one consumer and prevents adding others without editing the state machine.
The event indirection is precisely what keeps the domain free of consumer concerns.

**Persist the countdown to survive process death.** Rejected as out of scope: it needs
a foreground service or alarm. Elapsed workout time is already reconstructed from the
start timestamp where durability matters, and a resurrected countdown could
misrepresent the rest actually taken.

## Consequences

Positive:

- Extension is additive — a new consumer subscribes to the event.
- The timer is trivially testable in isolation.
- One countdown matches how lifters actually rest, and it survives navigation between
  screens.

Negative / accepted:

- Rest state is lost on process death by design.
- Completing a set supersedes any running rest (the single-countdown rule), which is
  intended.

**Architectural guarantee:** exactly one transient, session-scoped rest countdown
exists during an active workout, driven only by Complete, Undo, Skip, Finish, and
Discard, with its duration supplied by the completing exercise.

Related public documentation: docs/architecture/WORKOUT_LOGGING.md §5 (RestEffect event
model).
