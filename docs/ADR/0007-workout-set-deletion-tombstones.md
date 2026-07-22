# ADR-0007 — Workout set deletions propagate via a tombstone table

Date: 2026-07-22
Status: Accepted
Supersedes: nothing
Related: ADR-0001 (history is the source of truth), ADR-0002 (Room is the Android
source of truth), ADR-0003 (the backend is the system source of truth), SYNC.md,
TECH_DEBT TD-004

## Context

Every mutable entity on Android carries a `syncStatus` column, and the
synchronization engine uploads rows that are PENDING or FAILED. Deletion fits
that model for `Routine` and `RoutineExercise` because they are **soft**-deleted:
the row survives, flips a flag, and the engine can still see and upload it.

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

Out of scope: deletion propagation for `WorkoutExercise` (nothing in the UI
deletes one today) and bidirectional sync.
