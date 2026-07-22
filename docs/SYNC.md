Synchronization Strategy

Project: MyFitnessLog
Version: 1.0
Status: Approved — as released in Version 1.0.0 (22 July 2026)
Last Updated: July 20, 2026

⸻

1. Purpose

This document defines the synchronization strategy for MyFitnessLog.

The application follows an offline-first architecture where the Android application always remains usable regardless of network connectivity.

Synchronization is performed in the background and should never interrupt the user’s workout.

⸻

2. Synchronization Goals

The synchronization system must:

* Support complete offline usage.
* Never lose user data.
* Keep Android and backend data consistent.
* Retry failed synchronizations automatically.
* Minimize network usage.
* Be extensible for future multi-device support.

⸻

3. Core Principles

The synchronization system follows these principles.

Offline First

All user actions are performed locally.

Internet connectivity is never required to:

* Create routines
* Edit routines
* Start workouts
* Log sets
* Complete workouts

⸻

Local Database First

The Android application always writes to the Room database first.

UI screens always read from Room.

The backend is updated asynchronously.

⸻

Backend as Source of Truth

The backend stores the permanent synchronized copy of all workout history.

Once synchronization succeeds, Android and backend should contain equivalent data.

⸻

UUID-Based Synchronization

All entities use UUID Version 4.

UUIDs are generated on the Android device before synchronization.

No server-generated identifiers are used.

⸻

4. Synchronization Flow

The synchronization lifecycle is:

User Action
      │
      ▼
Room Database
      │
      ▼
UI Updated Immediately
      │
      ▼
Sync Queue
      │
      ▼
WorkManager
      │
      ▼
REST API
      │
      ▼
Spring Boot
      │
      ▼
PostgreSQL

The user should never wait for a network request before seeing their changes.

⸻

5. Synchronization Triggers

Synchronization begins when one of the following conditions is met:

* The application starts (SyncManager.onAppStart: registers the recurring
  schedule and requests an immediate pass).
* A new record is created, or a local record is modified — every repository
  write calls SyncTrigger.requestSync().
* A retry interval expires (WorkManager's exponential backoff).
* The recurring periodic sync fires (15 minutes, WorkManager's minimum).
* Internet connectivity becomes available — handled by WorkManager's
  NetworkType.CONNECTED constraint, not by a custom network callback.
* The user manually requests synchronization — SyncManager.syncNow() exists for
  this; no UI is wired to it yet.

All of these collapse into a single unique work item (ExistingWorkPolicy.KEEP),
so triggering redundantly is cheap and safe. Callers should err towards
triggering after every write rather than trying to predict when a sync is
worthwhile.

Requesting a sync is fire-and-forget: it returns immediately, never blocks the
caller, and never reports success. No repository method waits for
synchronization — that would reintroduce the network dependency this
architecture exists to remove.

⸻

6. Sync Direction

Version 1 supports one-way synchronization.

Android
      │
      ▼
Backend

The Android application is the only system that creates or modifies data.

The web application is read-only in Version 1.

⸻

7. Sync Units

Synchronization occurs at the entity level.

Entities synchronized include:

* Routine
* RoutineExercise
* WorkoutSession
* WorkoutExercise
* WorkoutSet
* Routine and RoutineExercise **deletions**, carried by the soft-deleted row
  itself: the engine inspects `isDeleted` and sends a DELETE rather than a
  create. Until M11 Phase 2 these rows were filtered out of the pending query and
  the deletion was silently discarded (TD-011).
* WorkoutSet deletions, carried by `workout_set_tombstone` rows (ADR-0007).
  A workout set is hard-deleted, so no row survives to carry the deletion and it
  needs a record of its own — the one case where a tombstone is warranted.

Reference data such as Exercise and ExerciseCategory is seeded by the backend and downloaded to Android.

⸻

8. Synchronization Order

Entities must be synchronized in dependency order.

Routine
        ↓
RoutineExercise
WorkoutSession
        ↓
WorkoutExercise
        ↓
WorkoutSet

This ensures that foreign key relationships remain valid.

⸻

Canonical Upload Sequence

The dependency order above is refined by one further rule: a workout session is
uploaded in two stages, and its terminal transition is sent last.

```
        Routine  (POST /routines)
           │
           ▼
   RoutineExercise  (POST /routines/{id}/exercises)


   WorkoutSession — start  (POST /workout-sessions)
           │
           ▼
   WorkoutExercise  (POST /workout-sessions/{id}/exercises)
           │
           ▼
      WorkoutSet  (POST /workout-exercises/{id}/sets)
           │
           ▼
  WorkoutSet — deletions  (DELETE /workout-sets/{id})
           │
           ▼
   WorkoutSession — complete / discard
        (PUT /workout-sessions/{id}/complete | /discard)
```

The terminal transition must come last because the backend permits adding or
modifying a session's exercises and sets only while that session is IN_PROGRESS.
Uploading the transition early would make the remaining children unwritable.

The deletion phase sits between the two for the same reason from both sides. It
runs *after* the set creates so a set that was created and deleted within one
offline stretch is not resurrected by a create later in the same pass, and
*before* the transition because a sealed session rejects deletions too. See
ADR-0007 for the tombstone mechanism that makes a hard-deleted set visible to a
pass at all.

**Routine and routine-exercise deletions need no separate phase.** They travel on
the soft-deleted row, so the existing Routine and RoutineExercise phases dispatch
on `isDeleted` and issue a DELETE instead of a create. Two rules make that safe:
a DELETE returning 404 counts as success (the row is already absent, which is the
goal state, and retrying an unchanged request would loop forever), and a pending
*create* whose parent routine was deleted in the same pass is skipped, because
the backend rejects additions to a soft-deleted routine permanently. Child
deletions still upload, since deleting by id converges regardless of the parent.

Sending the session start immediately (rather than deferring the whole workout
until it ends) preserves the session UUID on the backend from the first moment,
allows recovery if the app is killed mid-workout, and avoids one large upload
after a long session.

⸻

Reference Data Assumption

Version 1 assumes `Exercise` and `ExerciseCategory` already exist on the backend
before any user data is uploaded. They are seeded server-side and downloaded to
Android; the device never creates them.

This matters because `RoutineExercise` and `WorkoutExercise` reference an
exercise by foreign key — uploading one that the backend does not know would fail
permanently rather than transiently, and no amount of retrying would fix it. The
assumption holds while seeding is server-side and one-way, and must be revisited
if bidirectional synchronization or user-defined exercises are introduced.

⸻

9. WorkManager

Background synchronization is implemented using WorkManager.

Responsibilities include:

* Executing synchronization tasks.
* Retrying failed work.
* Respecting network availability.
* Continuing synchronization after application restarts.

Synchronization work requires network connectivity (NetworkType.CONNECTED). It
deliberately does NOT require charging, an unmetered network, or a healthy
battery: workout history is small, and delaying a user's data for days to save
marginal battery is the wrong trade for this application.

SyncWorker is intentionally minimal — recover stranded claims, run one engine
pass, map the result to success/retry. All synchronization logic lives in
SyncEngine, which has no WorkManager dependency and is unit-tested without it.

Stranded-claim recovery

The engine marks a row SYNCING before uploading it, and the pending queries
exclude SYNCING so two passes cannot upload the same row twice. If the process
dies mid-pass, nothing clears that claim and the row becomes permanently
invisible to synchronization. Every pass therefore begins by returning all
SYNCING rows to PENDING. The recovery writes only syncStatus and is idempotent.

⸻

10. Failure Handling

If synchronization fails:

* Keep all local data.
* Mark affected records as pending synchronization.
* Retry automatically.
* Do not discard data.

The user should continue using the application normally.

⸻

11. Conflict Strategy

Version 1 assumes a single Android device.

Therefore, synchronization conflicts are not expected.

If duplicate synchronization requests occur, backend operations must remain idempotent.

Future multi-device support may introduce conflict resolution strategies.

⸻

12. Idempotency

Synchronization endpoints must be idempotent.

Submitting the same entity multiple times should produce the same final database state.

The backend should identify records using UUIDs rather than insertion order.

⸻

13. Network Availability

The application should never block the user due to missing internet connectivity.

When offline:

* Save locally.
* Queue synchronization.
* Retry automatically later.

⸻

14. Data Integrity

Synchronization must preserve:

* UUIDs
* Relationships
* Timestamps
* Historical workout records

Synchronization must never modify completed workout history unexpectedly.

⸻

15. Sync State

Each synchronizable entity should maintain a synchronization state.

Possible states:

State	Description
PENDING	Waiting to synchronize
SYNCING	Synchronization in progress
SYNCED	Successfully synchronized
FAILED	Synchronization failed and will be retried

These states are used internally by the Android application and are not exposed to the user.

⸻

16. Retry Strategy

Failed synchronization attempts should use exponential backoff.

Example:

Attempt 1 → Immediate
Attempt 2 → 30 seconds
Attempt 3 → 2 minutes
Attempt 4 → 10 minutes
Subsequent attempts managed by WorkManager

The user should not be required to manually retry.

⸻

17. Security

Version 1 does not include authentication.

Future versions will secure synchronization using authenticated API requests.

The synchronization architecture should not assume anonymous access beyond Version 1.

⸻

18. Large Data Sets

Workout history is synchronized incrementally.

Only records that are not yet synchronized or have been modified locally should be transmitted.

Full database uploads are not performed.

⸻

19. Future Enhancements

Future versions may introduce:

* Bidirectional synchronization.
* Reconciling a deletion the backend rejects. Today a 4xx drops the tombstone
  and reports the failure, because one-way sync has nothing to reconcile
  against (ADR-0007).
* Deletion propagation for WorkoutExercise, if the UI ever gains that action.
* Multiple Android devices.
* Web editing.
* Conflict resolution.
* Sync progress indicators.
* Manual sync controls.
* Background media synchronization.

The Version 1 design should allow these capabilities without redesigning the synchronization architecture.

⸻

20. Error Recovery

If synchronization is interrupted:

* Resume from pending records.
* Do not restart the entire synchronization process.
* Preserve synchronization order.

Unexpected application termination must not result in data loss.

⸻

21. Logging

Synchronization events should be logged during development.

Examples:

* Sync started
* Sync completed
* Sync failed
* Retry scheduled
* Network unavailable

Production logging should avoid excessive verbosity and never include sensitive information.

⸻

22. Design Decisions

Room is the Android Source of Truth

The UI never reads directly from the network.

All screens observe the Room database.

⸻

Backend is the Permanent Source of Truth

The backend stores the authoritative synchronized copy of all user data.

⸻

Synchronization is Transparent

Users should not need to think about synchronization.

It should happen automatically whenever possible.

⸻

Historical Data is Never Lost

Workout history has the highest priority.

Synchronization failures must never result in loss of completed workout data.

⸻

23. Definition of Done

The synchronization system is considered complete when:

* The application functions fully offline.
* User actions are immediately persisted locally.
* Background synchronization occurs automatically.
* Failed synchronizations retry automatically.
* UUIDs remain consistent across Android and backend.
* Data integrity is preserved.
* Completed workout history is never lost.
* The synchronization process is transparent to the user.