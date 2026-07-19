Synchronization Strategy

Project: MyFitnessLog
Version: 1.0
Status: Approved
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

Synchronization should begin when one of the following conditions is met:

* Internet connectivity becomes available.
* A new record is created.
* A local record is modified.
* A retry interval expires.
* The application starts.
* The user manually requests synchronization (future enhancement).

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

9. WorkManager

Background synchronization is implemented using WorkManager.

Responsibilities include:

* Executing synchronization tasks.
* Retrying failed work.
* Respecting network availability.
* Continuing synchronization after application restarts.

Synchronization work should require network connectivity.

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