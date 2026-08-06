# ADR-0017 - The backend becomes readable, in three stages

Date: 2026-08-07
Status: Accepted. Stage 1 implemented 2026-08-07; Stages 2 and 3 are committed in
shape but not in detail.
Related: ADR-0001 (history is the source of truth), ADR-0002 (Room is the Android
source of truth), ADR-0003 (backend is the system source of truth), ADR-0004
(snapshot-based workout history), ADR-0007 (deletion tombstones), docs/SYNC.md,
docs/PRD.md, TD-010, TD-014, CODING_STANDARDS §20c

## Context

Synchronisation was built in one direction only. Every local write is uploaded to
the backend; nothing is ever read back. This is not a gap in the design so much as
an unfinished half of it:

- **ADR-0003 already says the backend is the system source of truth**, and that
  "other clients always retrieve data from the backend". Android is a client. It
  never retrieves. So the decision is true by declaration and false in practice.
- **ADR-0002 already permits the missing direction**: "successful synchronization
  updates Room if necessary, and Room propagates changes to the UI". Room being
  updated from the backend was always allowed. It simply was not built.

The Android client has no read path at all. `RoutineApi` and `WorkoutSessionApi`
declare only `@POST`, `@PUT` and `@DELETE`. Meanwhile the backend exposes
`GET /workout-sessions`, `GET /workout-sessions/{id}` and `GET /routines`, all
implemented, tested, and already consumed by the web client. The data is
retrievable; the phone just never asks.

The cost of this is not hypothetical. It has been paid three times:

1. **2026-07-22.** `connectedAndroidTest` uninstalled the app on the physical
   device. Two routines and four workout sessions were destroyed and could not be
   recovered, because synchronisation is one-way (CODING_STANDARDS §20c).
2. **2026-08-06.** Two completed sessions logged on the emulator sit on the
   backend and are invisible on the phone. To the user this reads as missing data,
   not as an architectural boundary.
3. **Continuously.** Three separate documents must warn "never uninstall". Those
   warnings are a safety rail bolted around a missing feature. An app whose
   documentation must tell you not to use a normal platform operation has moved a
   design problem onto the user.

Two product intentions now make the gap urgent rather than merely untidy:
**workouts should be editable**, and a future web client should be able to
**write**, not only read. Both require a defined rule for what happens when two
copies of a row disagree. Today that question has never arisen because there is
exactly one writer, so conflicts are impossible by construction. Adding a second
writer, or an edit to already-synced history, ends that.

## Decision

**The backend arbitrates. Room remains the local read and write path.**

These are separate concerns and are decided separately. Naming the backend as the
authority does not require routing the app's reads and writes through it.

### What is explicitly rejected

**The app will not read and write the backend directly**, with Room removed or
demoted to a view cache. This was considered and rejected on two grounds, either
of which is sufficient:

- **Offline-first is the product.** PRD §Objectives requires the app to "work
  completely offline", and §Problem Statement names poor offline support as the
  competitor weakness this product exists to beat. Gyms are basements with thick
  walls. An app that cannot log a set without signal is not this app.
- **The deployment cannot support it.** The free Render instance sleeps and was
  observed cold-starting in **100.8 seconds** (deploy log, 2026-08-05). A
  network-path write would stall the first set of a session for over a minute. No
  amount of spinner design makes that acceptable mid-workout.

### What changes

Room's *status* changes, not its position in the data flow. It stops being a
rival authority and becomes a **durable local cache and an outbox**. Reads still
come from Room, so the UI stays instant and offline-capable. Writes still land in
Room first, so nothing is lost when the network is absent. ADR-0002's data flow is
unchanged. What is added is the reverse edge that ADR-0002 already anticipated:
Room may be updated *from* the backend, and when the two disagree after all
pending local work has drained, the backend wins.

### Three stages

Each stage is independently useful, and each is a prerequisite for the next. They
are staged this way so that conflict machinery is built only when there is a
conflict to resolve, and not before.

**Stage 1 - Restore.** Hydrate Room from the backend **only when the local
database is empty**.

> **Correction, 2026-08-07 (during implementation).** This ADR originally claimed
> Stage 1 "requires no backend changes at all". That was wrong. It held for
> workout history, where `GET /workout-sessions/{id}` already nests exercises and
> sets, and was false for routines: `RoutineExerciseController` had no read
> endpoint of any kind, and `GET /routines/{id}` returned only name, description
> and display order. A routine's exercises could be created, updated, reordered
> and deleted, but never read, so a routine could be written to the backend and
> never reconstructed from it. Stage 1 therefore adds `RoutineDetailResponse` to
> the by-id endpoint, mirroring the workout detail shape. The claim was made from
> the API summary table without checking the controller.

The empty-database precondition is the entire point, not a simplification to be
apologised for. With nothing local to disagree with, there is no merge, no
tombstone reconciliation, and no last-write-wins rule to get subtly wrong. It is
the whole of the read path (endpoints, DTOs, download mappers, entity writes at
`SyncStatus.SYNCED`) with none of the convergence risk, which makes it the
cheapest possible way to prove that half of the system works. It also closes the
failure that has actually cost data: a lost, reset, or reinstalled phone.

**Stage 2 - Refresh.** Pull periodically, not only when empty.

The rule: **the backend wins for any row with no pending local change**. Rows the
outbox still owns are left untouched until they drain, so a refresh can never
discard work that has not been uploaded yet. This is what finally makes ADR-0003
true in practice, and it is what lets a second device see the first device's
history.

**Stage 3 - Edit.** Allow completed workouts to be edited, and define what happens
when two copies disagree.

This stage, and only this stage, **amends ADR-0001 and ADR-0004**. History being
an immutable snapshot is a deliberate decision, not an oversight: it is what makes
"previous performance" a stable input and what keeps a completed session
independent of later routine changes. Making it mutable is a real product change
and must be argued in its own ADR amendment rather than smuggled in as a
refactor.

The intended conflict policy is **last-write-wins on `updatedAt`, arbitrated by
the backend**. Every entity already carries `createdAt` and `updatedAt` through
JPA auditing (ADR-0006), so the required field exists on every row today. Two
details matter and are recorded now so they are not rediscovered later:

- **The server's `updatedAt` decides, not the device's clock.** Device clocks
  drift and can be set by the user. A device-authoritative timestamp would let a
  phone with a fast clock silently win every conflict forever.
- **Last-write-wins loses the other edit, silently.** That is acceptable for a
  single user editing their own history from one device at a time, and it stops
  being acceptable the moment there are real concurrent writers. The trigger for
  revisiting it is multi-user (V2), not multi-device.

## Alternatives considered

**Backend as the only store; drop the local database.** The user's initial
proposal, and the cleanest model on paper: one copy, no convergence, no
staleness. Rejected on the two grounds above (offline requirement, 100-second cold
start). Worth recording that this becomes less unreasonable if the backend ever
moves off a free tier that sleeps, but the offline requirement would still stand.

**Build full bidirectional sync in one step.** Rejected as building the hard part
first. Conflict resolution is the expensive, risky portion, and Stage 1 delivers
the recovery benefit without any of it. Staging also means the read path is proven
in isolation before conflict rules are layered on top, so a bug in the mappers is
not confused with a bug in the merge.

**Keep one-way sync and rely on the nightly database backups.** Rejected because
the backups protect the wrong side. The GitHub Actions dump protects the
*backend* from catastrophe; the exposure that has actually cost data is
phone-local state with no route home. A backup of the server cannot restore a
phone that cannot read the server.

**CRDTs, or an event-sourced log with replay.** Genuinely correct for concurrent
multi-writer editing, and enormous. Rejected as disproportionate for one user with
one writing client. The `updatedAt` approach is a deliberate trade of theoretical
correctness for something that can ship.

**Make history mutable immediately, without staging.** Rejected: it inverts the
risk order, taking on the one change that amends two existing ADRs before the read
path it depends on even exists.

## Consequences

Positive:

- A lost, reset, stolen, or reinstalled phone stops being a data-loss event. This
  is the first time the backend copy becomes reachable by the client that needs it.
- The "never uninstall" warnings in CODING_STANDARDS §20c, the release checklist,
  and the README become advisory rather than load-bearing.
- ADR-0003 becomes true in practice, not only by declaration.
- Stage 1 needs only one small backend addition (routine exercises on read; see
  the correction above). Everything else it requires was already built, tested and
  in production use by the web client.
- A writing web client becomes possible, because "who wins" will have an answer.

Negative / accepted:

- **Staleness becomes a real state.** Once Room can be updated from the backend,
  the app can display data that is briefly behind. It cannot today, because it only
  ever shows what it wrote itself.
- **Stage 2 makes TD-010 (unpaginated history) matter.** Pulling an unbounded
  history is the same scan problem TD-003 fixed on the backend, arriving on the
  client. Pagination stops being deferrable at Stage 2, not at Stage 1.
- **TD-014 is subsumed but also exposed.** A `WorkoutExercise` removed locally and
  left on the backend is invisible today; once the phone reads the backend back, a
  refresh could resurrect that empty row. TD-014 must be fixed before Stage 2, not
  merely before some later tidy-up.
- **Last-write-wins can lose an edit** with no warning to the user. Recorded above
  with its revisit trigger.
- **More surface to test.** Download mappers are a second, independent translation
  between DTOs and entities, and they can drift from the upload mappers. They need
  their own tests rather than an assumption of symmetry.
- **No read endpoint exposes `updatedAt`.** Every entity stores it (ADR-0006) and
  no response returns it, so restored rows are stamped with the restore instant
  instead. Harmless at Stage 1, where nothing compares timestamps, but Stage 3's
  last-write-wins cannot be built until the backend starts sending it. Discovered
  while implementing Stage 1 and recorded here so it is a known prerequisite
  rather than a late surprise.
