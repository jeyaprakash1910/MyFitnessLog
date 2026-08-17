# ADR-0007 — Workout set deletions propagate via a tombstone table

Date: 2026-07-22
Status: Accepted
Supersedes: nothing
Related: ADR-0001 (history is the source of truth), ADR-0002 (Room is the Android
source of truth), ADR-0003 (the backend is the system source of truth), SYNC.md,
TECH_DEBT TD-004

## Context

Every mutable entity on Android carries a `syncStatus` column, and the
synchronization engine uploads rows that are PENDING or FAILED. Deletion was
believed to fit that model for `Routine` and `RoutineExercise` because they are
**soft**-deleted: the row survives, flips a flag, and the engine can still see
and upload it.

> **That premise was false when this ADR was written.** See the amendment at the
> end of this document. The soft delete was recorded correctly, but the pending
> query filtered `isDeleted = 0` and the engine had no delete phase, so those
> deletions were discarded rather than propagated. The decision below is
> unaffected — a hard-deleted set still needs a record of its own — but the
> contrast it draws with routines did not hold in practice.

`WorkoutSet` is **hard**-deleted. Once the row is gone there is no record it ever
existed, so nothing is left for a pass to upload and the backend keeps a set the
phone no longer has. `WorkoutRepositoryImpl.deleteSet` therefore deliberately did
not even request a sync.

This was invisible while Android was the only client — the phone never re-reads
its own history from the backend. Milestone 10 introduces a second client that
does. The same user, looking at the same workout, would see a set on the web that
their phone does not show. Under ADR-0001 history correctness is this project's
highest-priority property, so an unbounded divergence between clients is not
acceptable debt to carry into M10.

The backend already exposes `DELETE /api/v1/workout-sets/{id}`, and that endpoint
is a no-op when the id is unknown. No backend change is required; the gap is
entirely on the client.

## Decision

Android records a hard-deleted set in a dedicated **tombstone table**,
`workout_set_tombstone` (Room v4), and the synchronization engine gains a phase
that issues the corresponding `DELETE` and then removes the tombstone.

Specifics that are part of the decision, not incidental:

* **The tombstone's primary key is the deleted set's own id.** Recording a
  deletion is therefore idempotent by construction — the same set cannot be
  queued twice.
* **The delete and the tombstone write are one Room transaction**
  (`WorkoutSetDao.deleteAndRecord`). A tombstone without the delete would ask the
  backend to remove a set the phone still shows; a delete without the tombstone
  is precisely the bug being fixed.
* **There is no `syncStatus` column.** A tombstone *is* the pending work: its
  existence is the queue, and a successful upload deletes it. Rows leave the
  database rather than accumulating.
* **`workoutSessionId` is denormalized onto the tombstone**, and the row has no
  foreign key. The tombstone must outlive the live graph it came from, and a
  cascade must never silently discard a pending deletion.
* **The deletion phase runs after the set uploads and before the session's
  terminal transition.** After, so a set created and deleted in one offline
  stretch is not resurrected by a create in the same pass. Before, because the
  backend refuses every write — deletions included — to a session it has sealed.
* **A 4xx rejection drops the tombstone** rather than retrying it. An unchanged
  request will fail identically forever, so retaining it would retry on every
  pass indefinitely. The failure is reported in the pass summary. Transient
  failures (network, 5xx) keep the tombstone and additionally block the session's
  transition, so a workout is never sealed while one of its sets is still
  pending removal.

## Alternatives considered

**Soft-delete flag on `WorkoutSet`.** The cheapest change and consistent with the
other mutable entities. Rejected because it pushes `isDeleted = 0` into every
history read path permanently — the history DAO, the detail query, the grouped
summary — and a query that forgets the filter silently shows a deleted set. That
trades a bounded, one-time cost in the sync path for an unbounded, easily
forgotten cost in the correctness-critical read path. It also means rows never
leave the device.

**Pending-delete queue as a separate sync mechanism.** Rejected as overkill: it
is a second synchronization pathway running alongside the status column, for a
one-way V1 sync with a single entity that needs it.

## Consequences

Positive:

* The backend converges on what the phone actually holds, so the web client and
  the phone show the same workout.
* An append-only delete log is the shape a future bidirectional sync needs
  anyway; this is not throwaway work.
* No read path changes. History queries are untouched, so snapshot integrity and
  the existing history tests are unaffected.

Negative / accepted:

* One more table and a schema migration (v3 → v4, purely additive).
* A deletion that is rejected by the backend is dropped rather than reconciled.
  With one-way sync there is no mechanism to reconcile it against, and the
  failure is surfaced in the pass summary; a bidirectional sync would revisit
  this.
* Tombstones created while the backend is unreachable persist until a successful
  pass. That is the intended behaviour, and the volume is bounded by how many
  sets a user deletes between syncs.

Out of scope: deletion propagation for `WorkoutExercise` and bidirectional sync.

> **Amendment — 2026-07-30.** The original text above read "deletion propagation
> for `WorkoutExercise` (nothing in the UI deletes one today)". That is no longer
> accurate: the workout-logging redesign lets a user remove an exercise from an in-progress
> session. Removing an exercise tombstones its **sets** via this mechanism, but the
> `WorkoutExercise` **row** deletion is still not propagated to the backend, so an
> already-synced removed exercise can linger (empty) on a not-yet-completed
> session. Propagation remains deferred; the limitation is tracked as **TD-014**
> and its fix mirrors this ADR.


---

## Amendment — 2026-07-22 (Milestone 11, Phase 2)

### What this ADR asserted

That `Routine` and `RoutineExercise` deletions already propagated, because a soft
delete leaves a row the engine can see and upload. That contrast was the whole
reason `WorkoutSet` looked exceptional and warranted a tombstone.

### How it was disproved

Phase 1 read the live database on a physical device and found a routine exercise
soft-deleted at 23:38, still `PENDING` after six subsequent sync passes, and
still present on the backend. Measured divergence for one routine: 7 rows on the
device, 8 on the backend.

The cause was two-fold and entirely client-side:

* `RoutineDao.getPendingSync()` and `RoutineExerciseDao.getPendingSync()` both
  filtered `WHERE isDeleted = 0`, excluding exactly the rows whose deletion
  needed uploading. The DAO comment even said propagation was "deferred to a
  later phase" — but excluding the row did not defer the deletion, it discarded
  it.
* The engine had no delete phase for these entities, though
  `RoutineApi.deleteRoutine` and `deleteRoutineExercise` already existed and were
  never called.

### What changed

The pending queries no longer filter on `isDeleted`, and the engine dispatches on
it: a soft-deleted row is sent as a DELETE instead of a create, inside the
existing routine phases. On success the row keeps `isDeleted = true` and becomes
`SYNCED`, so it is hidden from the UI and never re-uploaded.

**No third synchronization mechanism was introduced.** The tombstone pattern in
this ADR remains specific to `WorkoutSet`, which is genuinely different: it is
hard-deleted, so no row survives to carry the deletion. Where a row does survive,
the row itself is the carrier.

Two supporting rules were needed:

* **A DELETE that returns 404 counts as success.** The goal state is "absent from
  the backend", and 404 means it already is. This is reachable normally — a
  routine created and deleted while offline is never uploaded, so the DELETE
  names an id the backend has never seen. Without this the row would be marked
  FAILED and retried on every pass forever.
* **A pending child create is skipped when its parent routine was deleted this
  pass.** The backend resolves a routine's *active* row before adding to it, so
  such a create would 404 permanently. Child *deletions* still upload, because
  deleting by id converges regardless of the parent.

### Alternatives rejected

* **A tombstone table for routines**, mirroring `WorkoutSet`. Rejected: it adds a
  third synchronization model to solve a problem the existing one already
  encodes. The soft-delete column *is* the tombstone when the row survives.
* **Hard-deleting locally and recording a tombstone.** Same objection, and it
  would discard the soft-delete semantics the rest of the app relies on for
  history and undo-adjacent behaviour.
* **Leaving the filter and adding a separate "deleted rows" query.** Functionally
  equivalent to removing the filter, but with two queries to keep in step.

### Consequence for this document

The Context section above no longer claims routine deletions propagate. The
mechanism it selects for `WorkoutSet` is unchanged and remains correct.
