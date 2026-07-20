Project Roadmap

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: July 20, 2026

⸻

1. Purpose

This roadmap defines the implementation plan for MyFitnessLog Version 1 and
tracks what has been built so far.

The objective is to build the application incrementally while maintaining a
working codebase at every milestone.

Each phase builds upon the previous phase and should be completed before starting
the next.

This document is intended to be the single, quick-to-read picture of "what is
done and what is next" for the whole codebase.

⸻

2. Development Principles

The implementation follows these principles:

* Build vertically rather than horizontally.
* Keep the application runnable after every milestone.
* Complete one feature before starting another.
* Test continuously.
* Commit small, meaningful changes.
* Avoid partially implemented features.

⸻

3. Current State Snapshot (as of July 20, 2026)

This section reflects what actually exists in the repository today.

Repository layout:

* backend/ — Spring Boot API (Java 21, Maven)
* android/ — Android app (Kotlin, single Gradle module)
* docs/    — architecture, database, API, sync, coding standards, ADRs
* web/     — not started

Backend (implemented):

* Spring Boot app, PostgreSQL, Flyway, JPA auditing, OSIV disabled, global
  exception handler, SLF4J logging, SpringDoc OpenAPI, health endpoint.
* Flyway V1 creates ALL eight tables (User, ExerciseCategory, Exercise, Routine,
  RoutineExercise, WorkoutSession, WorkoutExercise, WorkoutSet). V2/V3 seed 8
  categories and 40 exercises.
* Entities / repositories / services / controllers / mappers / DTOs exist for
  ExerciseCategory and Exercise only.
* Endpoints: GET /health, GET /exercise-categories, GET /exercises,
  GET /exercises/{id}, GET /exercises/search.
* Not yet built: entities/services/controllers for Routine, RoutineExercise,
  WorkoutSession, WorkoutExercise, WorkoutSet; any write (POST/PUT/DELETE)
  endpoints; automated backend tests (backend/src/test is currently empty).

Android (implemented):

* Single Gradle module; Hilt DI, Jetpack Compose, Navigation Compose, Room,
  Retrofit + OkHttp + kotlinx.serialization, Material 3 theme, version catalog,
  Gradle wrapper.
* Room infrastructure: database, UUID/Instant converters, exported schema
  (committed). Reference entities + DAOs for ExerciseCategory and Exercise.
* Network + repository boundary for the exercise library (APIs, DTOs, mappers,
  repository interfaces + implementations, Hilt bindings).
* Exercise Library feature end-to-end: ViewModel, immutable UI state, Compose
  screen (Home), local search and category filtering (Room-only, offline-first).
* 37 automated tests pass (converters, DAO, repositories, ViewModel, Compose UI)
  executed on the JVM via Robolectric.
* Not yet built: WorkManager; any feature beyond the exercise library; on-device
  / emulator run (no AVD installed — verification so far is test-based).

Approved sequencing decision (July 20, 2026):

* The backend write APIs for Routine / Workout / History are resequenced OUT of
  the per-feature Android milestones and INTO a dedicated backend milestone just
  before Synchronization. Rationale: sync is one-way (Android → backend) and
  deferred, so write endpoints have no caller until sync exists; building them
  earlier is speculative (YAGNI). Android features are built offline-first
  against Room first. The frozen contracts in API_SPECIFICATION.md keep the two
  sides from drifting.

⸻

4. Milestone Overview

Milestone	Status
Foundation Documents	✅ Completed
M1 Backend Foundation	✅ Completed
M2 Backend Exercise Library	✅ Completed
M3 Android Foundation	✅ Completed
M4 Android Exercise Library	✅ Completed (built within M3 phases)
M5 Routine Management (Android)	⬜ Next
M6 Workout Logging (Android)	⬜ Pending
M7 Workout History (Android)	⬜ Pending
M8 Backend Sync APIs	⬜ Pending (resequenced)
M9 Synchronization	⬜ Pending
M10 Web Application	⬜ Pending
M11 Testing & Polish	⬜ Pending
M12 Version 1 Release	⬜ Pending

Immediate small close-out (not a milestone): a single on-device / emulator run
to confirm the M3/M4 exit criteria (launch, navigation, exercise list/search/
category) that could not be executed without an emulator.

⸻

5. M1 — Backend Foundation ✅ Completed

Goal: establish the backend project structure and infrastructure.

Delivered: Spring Boot project, Maven, PostgreSQL, Flyway, JPA config, global
exception handling, logging, health endpoint, OpenAPI/Swagger, initial structure.

Exit criteria (met): app starts, DB connects, Flyway runs, Swagger available,
health endpoint responds.

⸻

6. M2 — Backend Exercise Library ✅ Completed

Goal: implement the master exercise library.

Delivered: Exercise and ExerciseCategory entities, repositories, services,
controllers, mappers, DTOs, seed data (8 categories, 40 exercises).

Exit criteria (met): library loads, search works, categories display. Verified
end-to-end against a fresh PostgreSQL database.

Known gap: no automated backend tests yet (addressed in M11).

⸻

7. M3 — Android Foundation ✅ Completed

Goal: create the Android application skeleton and first vertical slice.

Delivered across five reviewed phases:

* Phase 1 — Skeleton: Application (Hilt), single Activity, Material 3 theme,
  Navigation Compose graph (Home/Workout/History/Settings placeholders), DI
  modules (Database/Network/Dispatcher), Room infrastructure + converters,
  Retrofit/OkHttp/kotlinx.serialization, version catalog, Gradle wrapper.
* Phase 2 — Room reference-data layer: ExerciseCategory/Exercise entities, DAOs,
  database, exported schema (committed).
* Phase 3 — Network + repository boundary: Retrofit APIs, DTOs, hand-written
  mappers, repository interfaces + implementations, Hilt bindings.
* Phase 4 — Exercise Library presentation: ViewModel, immutable UI state, Compose
  screen wired to Home, offline-first state precedence.
* Phase 5 — Local search + category filtering: Room-only, no backend search.

Exit criteria: launch, navigation, Room init, DI — verified by 37 automated
tests. Remaining: one on-device confirmation (no emulator installed).

Deferred deliverable: WorkManager — intentionally moved to M9 (Synchronization),
where it is actually used.

⸻

8. M4 — Android Exercise Library ✅ Completed (within M3)

Goal: implement the Android exercise library screens using the backend APIs.

Delivered: exercise repository (download + local cache), exercise list screen
(Compose, offline-first), exercise search (local, Room-only), category browsing /
filtering (local, Room-only).

Exit criteria: library loads, search works, categories display — verified by
tests. Remaining: on-device confirmation.

⸻

9. M5 — Routine Management (Android, offline-first) ⬜ Next

Goal: implement workout templates on Android, fully offline against Room.

Deliverables:

* Room: Routine and RoutineExercise entities + DAOs, INCLUDING a syncStatus
  column from creation (these are the first mutable entities; adding syncStatus
  now avoids a destructive Room migration later).
* The first mutable repository — establishes and documents the mutable-repository
  API-shape convention (observe + write + sync-trigger).
* ViewModels + Compose screens: Home (routine list), Routine details, Create /
  Edit routine, reorder exercises, duplicate routine, delete (local soft-delete).

Constraints: no backend calls; no synchronization. All data lives in Room.

Exit criteria: users can fully manage routines offline; data persists locally;
verified by automated tests (DAO, repository, ViewModel, Compose UI) and a device
pass if an emulator is available.

⸻

10. M6 — Workout Logging (Android, offline-first) ⬜ Pending

Goal: implement active workout tracking on Android, fully offline against Room.

Deliverables:

* Room: WorkoutSession, WorkoutExercise, WorkoutSet entities + DAOs (with
  syncStatus).
* Snapshot-at-workout-start logic (Routine/RoutineExercise → WorkoutExercise).
  This is the first non-trivial business rule and is a good candidate for a
  single focused UseCase (not a blanket UseCase layer).
* Compose: workout screen, workout timer, rest timer, add/edit/delete sets,
  complete workout, discard workout.

Constraints: offline only; no synchronization.

Exit criteria: complete workouts can be recorded and stored locally; historical
snapshots are created correctly; verified by tests.

⸻

11. M7 — Workout History (Android, offline-first) ⬜ Pending

Goal: implement historical workout viewing on Android from local snapshots.

Deliverables: history screen, workout detail screen, read-only completed workouts.

Constraints: offline only; reads from Room snapshots.

Exit criteria: users can browse history; details display accurately; snapshot
integrity preserved; verified by tests.

⸻

12. M8 — Backend Sync APIs ⬜ Pending (resequenced)

Goal: implement the backend endpoints required by synchronization and the web
app, against the finalized contracts in API_SPECIFICATION.md.

Deliverables:

* Entities / repositories / services / controllers / mappers / DTOs for Routine,
  RoutineExercise, WorkoutSession, WorkoutExercise, WorkoutSet.
* Write endpoints (POST/PUT/DELETE) for routines and routine exercises; workout
  session lifecycle (start/complete/discard); workout exercises and sets.
* Read endpoints for workout history and details (consumed by the web app).
* Idempotent, UUID-keyed upserts so repeated sync submissions are safe.

Notes:

* No new Flyway migrations are required for the tables themselves — V1 already
  created all eight. Migrations are only needed if columns/constraints change.
* Backend automated tests should be written alongside these endpoints.

Exit criteria: all endpoints behave per the API specification, return correct
status codes, use DTOs, and are covered by tests.

⸻

13. M9 — Synchronization ⬜ Pending

Goal: synchronize Android-created data to the backend.

Deliverables: WorkManager integration (introduced here), sync queue, pending-sync
processing in dependency order, exponential-backoff retry, sync status tracking,
and reliance on the idempotent backend endpoints from M8.

Exit criteria: offline-created data synchronizes successfully; retry works; no
data loss; verified by tests.

⸻

14. M10 — Web Application ⬜ Pending

Goal: build the read-only web client for viewing workout history.

Deliverables: React app, workout history page, workout detail page, responsive
layout, API integration.

Depends on: M8 (endpoints) and M9 (data actually synced to the backend).

Exit criteria: history matches backend data; details render correctly; responsive
on desktop and tablet.

⸻

15. M11 — Testing & Polish ⬜ Pending

Goal: improve quality and prepare for release.

Deliverables:

* Backend: unit and integration tests (the current backend test gap is closed
  here at the latest, ideally earlier alongside M8).
* Android: consolidated UI, repository, and ViewModel testing; at least one full
  on-device / emulator pass of the core flows.
* General: performance improvements, bug fixes, documentation updates.

Exit criteria: critical bugs resolved; core workflows verified; documentation
matches implementation.

⸻

16. M12 — Version 1 Release ⬜ Pending

Before releasing Version 1:

* All milestones completed.
* All APIs documented.
* Database migrations verified.
* Android application stable.
* Backend stable.
* Web application stable.
* Documentation updated.
* GitHub repository organized.
* Release tag created.

⸻

17. Cross-Cutting Recommendations (approved)

* Add syncStatus to every mutable entity at creation (M5/M6), never retrofit it.
* Keep API contracts (API_SPECIFICATION.md) frozen as the shared spec so the
  Android-first sequencing does not cause drift.
* Introduce UseCases incrementally, only where logic is non-trivial or shared
  (first expected in M6, the workout snapshot).
* Decide on an emulator/AVD early in M5, since UI becomes interactive; otherwise
  plan a single consolidated device pass before Release.
* Close the backend automated-test gap alongside M8 rather than deferring all of
  it to M11.

⸻

18. Future Versions

Version 2: Authentication, multi-user support, user profiles, dashboard.

Version 3: Weight tracking, sleep tracking, water intake, progress photos.

Version 4: Health Connect, Apple Health, wearable integration, cloud backup.

Version 5: Nutrition tracking, analytics dashboard, AI insights, recommendations.

⸻

19. Success Criteria

Version 1 is considered complete when:

* Users can create workout routines.
* Users can perform complete workouts offline.
* Workout history is permanently stored.
* Android synchronizes with the backend automatically.
* Workout history is viewable on the web.
* All documentation is consistent with the implementation.
* The application is stable, maintainable, and ready for future expansion.
