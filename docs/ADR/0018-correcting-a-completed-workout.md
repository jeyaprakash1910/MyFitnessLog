# ADR-0018 - Correcting a completed workout

Date: 2026-08-08
Status: Accepted
Amends: ADR-0004 (snapshot-based workout history)
Related: ADR-0003 (backend is the system source of truth), ADR-0006 (timestamp
auditing), ADR-0007 (deletion tombstones), ADR-0011 (read-only workout
projections), ADR-0017 (backend convergence in three stages, Stage 3),
docs/API_SPECIFICATION.md, docs/SYNC.md

## Context

Completed workouts cannot be changed. The backend rejects any write to a session
that is not `IN_PROGRESS` with a 409, and the Android UI marks a finished session
read-only. That was deliberate: ADR-0004 exists because a completed workout must
keep reflecting what actually happened, whatever anyone later does to the routine
it came from.

It has also been wrong in one specific way since Milestone 1. A logged set can be
mistaken. You type 80 when you lifted 85, or tap complete on a set you did not
finish, or realise afterwards that you did a fourth set. Today the record is
simply wrong and nothing can be done about it, which is a poor answer for the one
thing this application exists to record faithfully.

**ADR-0004 already anticipated this.** Its implementation guidelines say
"completed workout history remains immutable **during normal application usage**"
and "corrections to completed workouts are permitted only through the dedicated
workout edit flow". That flow was never built. This ADR defines it, so this is
less a reversal of ADR-0004 than the delivery of a clause it deliberately left
open.

ADR-0017 anticipated that Stage 3 would amend both ADR-0001 and ADR-0004. On
inspection it amends only ADR-0004: ADR-0001 is about not persisting derived
recommendations, which this does not touch.

## Decision

A **completed** workout may be corrected. What may be corrected is deliberately
narrower than "anything on the record".

### What becomes editable

Set-level performance on a `COMPLETED` session:

* weight, repetitions, RPE, RIR, set category, completion flag
* adding a set that was performed but never logged
* deleting a set that was logged but not performed

These are all statements about **what the user did**, which is exactly what a
correction is for.

### What stays locked, and why

**The planning snapshot on a completed session stays immutable.** Exercise name,
exercise order, target sets, target rep range and target rest remain unwritable
once the session is no longer `IN_PROGRESS`, and so does adding or removing an
exercise. Those fields record **what the plan was on the day**, and rewriting
them is the precise failure ADR-0004 was written to prevent: history that quietly
changes to match a later opinion. Correcting a performance is not the same act as
rewriting an intention, and only the first is permitted here.

**A discarded workout stays immutable.** `DISCARDED` means the user threw the
session away. It is a deletion with a tombstone, not a record, so there is
nothing to correct. Writes to it keep returning 409.

**Nothing is editable while the session is `IN_PROGRESS`** by this route, because
that is the ordinary logging path and already works.

### Conflict resolution

**Last-write-wins, arbitrated by the server's `updatedAt`.** No new machinery is
required, which is worth stating explicitly because ADR-0017 implied there would
be:

* Every row already carries a server-assigned `updatedAt` (ADR-0006), and read
  endpoints have returned it since 2026-08-08.
* Stage 2's refresh already resolves the download direction: a row with no pending
  local change takes the backend's value, and a row the outbox still owns is left
  alone. A correction made on the phone is a pending local change, so it is
  protected until it uploads, after which the backend's copy is authoritative.
* The upload direction is an idempotent upsert keyed by id, so an edit that
  reaches the backend simply lands.

The two properties ADR-0017 asked to be recorded still hold and are restated here
because they are easy to lose:

* **The server's clock decides, never the device's.** Device clocks drift and can
  be set by the user, and a device-authoritative timestamp would let a phone with a
  fast clock win every conflict forever.
* **Last-write-wins loses the other edit, silently.** Acceptable for one person
  editing their own history from one device at a time. The trigger for revisiting
  it is multi-user (V2), not multi-device.

## Consequences

### Benefits

* A wrong number can be fixed, which is the point.
* The guarantee ADR-0004 actually cared about is preserved: no later change to a
  routine, an exercise catalogue entry or a target can alter a past workout. Only
  an explicit correction to a performance can, and only by the person who
  performed it.
* No schema change, no new conflict mechanism, no new sync direction.

### Trade-offs

* **Derived values change retroactively.** "Previous performance" is computed from
  history (ADR-0001), so correcting a set changes what future workouts suggest.
  That is the desired behaviour rather than a side effect: the suggestion should
  follow the corrected truth.
* **An audit trail is not kept.** A correction overwrites. The previous value is
  not retained, so there is no way to see that a set was edited or what it was
  before. That is a deliberate omission for a single-user application, and the
  point at which to revisit it is the same as for last-write-wins: more than one
  writer.
* **The rules are asymmetric and need explaining in the UI.** A user who can
  change a weight but not a target rep range will want to know why. The edit
  surface has to make the boundary obvious rather than failing with an error after
  the fact.

## Alternatives considered

**Allow editing everything on a completed session.** Simplest to build and it
destroys the guarantee ADR-0004 exists for. A user correcting a weight is doing
something different from a user retconning what they had planned to do, and
collapsing the two removes the ability to trust old records at all.

**Versioned history: keep the original and store corrections as revisions.**
Correct, auditable, and considerably more: another table, a projection to resolve
"current" values, and every read path taught about revisions. Rejected as
disproportionate for one user with one device correcting their own typos. This is
the alternative to revisit if an audit trail is ever needed, and it composes with
the decision above rather than replacing it.

**Delete and re-log the workout.** Possible today with no new code: discard the
session and log it again. Rejected as an answer because it loses the original
timestamps, changes what the history says about when training happened, and asks
the user to retype an entire session to fix one digit.

**Make corrections a separate "amendment" entity rather than an edit.** Keeps
history append-only, which is appealing. Rejected for the same reason as
versioning: it needs a resolution step on every read, and ADR-0011's read-only
projections would all have to learn about it.

## Implementation notes

* The backend guard changes from "the session must be `IN_PROGRESS`" to "the
  session must be `IN_PROGRESS` or `COMPLETED`" for **set** writes only. Exercise
  writes keep requiring `IN_PROGRESS`.
* `DISCARDED` must be rejected explicitly rather than by omission, so that a new
  status added later does not silently become editable.
* The Android edit surface is a separate increment. This ADR and the backend
  contract land first, so the client is built against a contract that already
  exists and is tested, rather than the two being designed against each other.
