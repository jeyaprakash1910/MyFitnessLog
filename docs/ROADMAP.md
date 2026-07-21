Project Roadmap

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: July 22, 2026

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

3. Current State Snapshot (as of July 22, 2026)

This section reflects what actually exists in the repository today. It is the
authoritative status summary; a new contributor (human or AI) should be able to
orient from this plus ANDROID_ARCHITECTURE.md and ANDROID_FLOW.md alone.

Repository layout:

* backend/ — Spring Boot API (Java 21, Maven)
* android/ — Android app (Kotlin, single Gradle module)
* docs/    — architecture, database, API, sync, coding standards, ADRs
* web/     — not started

Backend status (M1–M2 and M8 Backend Sync APIs complete):

* Spring Boot app, PostgreSQL, Flyway, JPA auditing, OSIV disabled, global
  exception handler (400/404/409/500 envelope), SLF4J logging, SpringDoc OpenAPI,
  health endpoint.
* Flyway V1 creates ALL eight tables (User, ExerciseCategory, Exercise, Routine,
  RoutineExercise, WorkoutSession, WorkoutExercise, WorkoutSet). V2/V3 seed 8
  categories and 40 exercises; V4 seeds the single default user (single-user V1).
* Entities / repositories / services / controllers / mappers / DTOs now exist for
  ALL resources (ExerciseCategory, Exercise, Routine, RoutineExercise,
  WorkoutSession, WorkoutExercise, WorkoutSet).
* Endpoints: the read-only exercise/category/health APIs PLUS the full write/read
  contract in API_SPECIFICATION.md — routines (CRUD + duplicate), routine
  exercises (add/update/remove/reorder), workout sessions (history/detail/start/
  complete/discard) and their exercises and sets. Idempotent UUID-keyed upserts
  (201 create / 200 replay); valid status transitions (409 on illegal);
  completed/discarded workouts are immutable; the detail endpoint returns the
  full nested snapshot assembled in-transaction (OSIV disabled).
* 86 automated backend tests (JUnit 5 + MockMvc) run against a real PostgreSQL
  database — including an end-to-end sync-graph idempotency proof. The backend
  test gap identified in M2 is now closed. (Local PostgreSQL is used to execute
  the tests; the same tests are portable to Testcontainers on a Docker-capable
  machine — see M11 / cross-cutting notes.)
* The default user is attached server-side; the Android client sends no userId.

Android status (M3–M7 complete; next milestone is M9 Synchronization):

* Foundation: single Gradle module; Hilt DI, Jetpack Compose, Navigation Compose,
  Room, Retrofit + OkHttp + kotlinx.serialization, Material 3, version catalog,
  Gradle wrapper. Android SDK installed locally; NO AVD/emulator.
* Room database version 3, exportSchema on (schemas v1/v2/v3 committed):
  - Reference (download-only): ExerciseCategory, Exercise.
  - Routine templates (mutable): Routine, RoutineExercise.
  - Workout history (mutable, immutable once completed): WorkoutSession,
    WorkoutExercise, WorkoutSet.
  - Converters: UUID(String), Instant(epoch millis), SyncStatus, WorkoutStatus
    + SetCategory (by name), BigDecimal(plain string, for weight/RPE/RIR).
* Repositories: ExerciseCategoryRepository, ExerciseRepository (read-only);
  RoutineRepository, WorkoutRepository (mutable); WorkoutHistoryRepository
  (read-only, over the immutable snapshot tables). Use case: StartWorkoutUseCase.
* Implemented features (all offline-first, Room-only, no network calls yet):
  - Exercise Library: initial download + local cache, list, local search,
    category filtering.
  - Routine Management: routine list (Home), routine detail, create/edit
    (rename, add/remove exercises via picker, reorder, edit targets), duplicate,
    soft-delete.
  - Workout Logging: start-from-routine (snapshot) OR manual/ad-hoc workout
    (routineId = null), or resume the active session (single-active invariant,
    atomic Room transaction); active workout screen; add exercises (manual, via
    the shared picker); add/edit/delete/toggle sets; complete/discard; completed
    workouts are immutable; workout elapsed timer (derived from startedAt) +
    transient rest countdown timer.
* Navigation: Home = routine list; nested routine detail / edit / add-exercise;
  Workout tab = active workout screen (optional routineId starts a routine
  workout; the "No active workout" state offers a manual start; else resumes the
  active one); the exercise picker is shared between routine editing and manual
  workouts; Settings is a placeholder.
  - Workout History: read-only history list (completed workouts only; DISCARDED
    and IN_PROGRESS excluded; newest first) showing date, derived duration,
    routine/manual indicator, exercise count, and a notes preview; read-only
    workout detail screen showing workout metadata + every snapshotted exercise
    and set (weight, reps, category, RPE), grouped and ordered from the snapshot.
    Built on WorkoutHistoryRepository over the existing snapshot tables — no
    schema change, no writes, snapshot integrity preserved. History is now a
    real top-level destination; Workout Detail is a full-screen drill-down.
* 172 automated tests pass, executed on the JVM via Robolectric (converters,
  DAOs, repositories, StartWorkoutUseCase, WorkoutClock, RestTimer, ViewModels,
  Compose UI, history DAO/repository/ViewModels/Content, formatting helpers).
* Not yet built: backend write APIs (M8); WorkManager / synchronization (M9);
  web client (M10); any on-device/emulator run (no AVD — verification is
  test-based only).

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
M5 Routine Management (Android)	✅ Completed
M6 Workout Logging (Android)	✅ Completed (all 5 phases)
M7 Workout History (Android)	✅ Completed (all 4 phases)
M8 Backend Sync APIs	✅ Completed (all 6 phases)
M9 Synchronization	⬜ Next
M10 Web Application	⬜ Pending
M11 Testing & Polish	⬜ Pending
M12 Version 1 Release	⬜ Pending

Current milestone: M9 (Synchronization).
Test count: 172 automated Android tests (JVM/Robolectric) + 86 backend tests
(JUnit 5/MockMvc over real PostgreSQL) = 258 passing.
Database version: Android Room v3; backend Flyway v4 (adds the default-user seed).
Backend: full write/read APIs per API_SPECIFICATION.md.  Sync: not started (M9).

Deferred close-out (not a milestone): a single on-device / emulator run to
confirm the Android exit criteria (launch, navigation, routine + workout flows)
that cannot be executed without an AVD.

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

9. M5 — Routine Management (Android, offline-first) ✅ Completed

Goal: implement workout templates on Android, fully offline against Room.

Delivered:

* Room: Routine + RoutineExercise entities/DAOs with syncStatus from creation
  (the first mutable entities). RoutineExercise carries planned targets
  (targetSets/min/maxReps/rest/notes) to match ANDROID_FLOW and the backend's
  NOT NULL columns.
* RoutineRepository — the first mutable repository; establishes the
  mutable-repository convention (observe + write ops; injected Clock; PENDING
  sync status). Database v2.
* Screens: routine list (Home), detail, create/edit (rename, add exercise via a
  reusable exercise picker, remove, reorder, edit targets), duplicate,
  soft-delete. Navigation drills down from Home; Home now shows routines.

Constraints honoured: no backend calls; no synchronization; all data in Room.

Exit criteria: met — full offline routine management, verified by automated
tests (DAO, repository, ViewModel, Compose UI).

⸻

10. M6 — Workout Logging (Android, offline-first) ✅ Completed

Goal: implement active workout tracking on Android, fully offline against Room.

Phase status:

* Phase 1 — Workout data layer ✅ WorkoutSession/WorkoutExercise/WorkoutSet
  entities + DAOs; WorkoutStatus + SetCategory enums; BigDecimal + enum
  converters; database v3. History tables are never soft-deleted.
* Phase 2 — Repository + StartWorkoutUseCase ✅ StartWorkoutUseCase snapshots a
  routine into immutable WorkoutExercise rows inside an atomic Room transaction,
  enforcing the single-active-session invariant. WorkoutRepository owns set CRUD
  and complete/discard, enforcing completed-workout immutability.
* Phase 3 — Active workout UI ✅ WorkoutViewModel + screen: start/resume, log
  sets (add/edit/delete/toggle), complete → History, discard → Home; read-only
  once completed. Routine Detail gained a "Start Workout" action; Workout tab is
  the active-workout screen.
* Phase 4 — Timers ✅ WorkoutClock (pure, elapsed = now − startedAt, frozen at
  endedAt) and RestTimer (transient countdown: start/restart/cancel/skip), both
  independently unit-tested. No service/notification/WorkManager.
* Phase 5 — Manual (ad-hoc) workouts ✅ Generalized StartWorkoutUseCase to
  accept a nullable routineId; added WorkoutRepository.addExercise; generalized
  the exercise picker to add to a routine OR an active workout. One workout
  domain — no parallel implementations.

Constraints honoured: offline only; no synchronization; no background execution.

Exit criteria: met — routine and manual workouts recorded and stored locally;
snapshots created correctly and immutable; verified by tests. Remaining: a device
pass once an AVD is available.

⸻

11. M7 — Workout History (Android, offline-first) ✅ Completed

Goal: implement historical workout viewing on Android from local snapshots.

Delivered across four reviewed phases:

* Phase 1 — Data layer: a dedicated read-only WorkoutHistoryDao and
  WorkoutHistoryRepository over the existing snapshot tables (WorkoutSession/
  Exercise/Set). Queries return COMPLETED sessions only (DISCARDED/IN_PROGRESS
  excluded), newest first, plus a grouped summary query for exercise counts. No
  new entities, no schema change (database stays v3).
* Phase 2 — History list: WorkoutHistoryScreen/Content/UiState/ViewModel showing
  one card per completed workout (date, derived duration, routine/manual
  indicator, exercise count, notes preview), with an empty state. Replaced the
  History placeholder; wired History → Workout Detail navigation.
* Phase 3 — Workout Detail: read-only WorkoutDetailScreen showing workout
  metadata and every snapshotted exercise + set (weight, reps, category, RPE),
  grouped under each exercise in captured order. No mutation controls. Reused
  the Phase 1 repository (no new queries).
* Phase 4 — Polish: shared formatting helpers (HistoryFormatting), centralized
  duration/notes helpers, a bodyweight (zero-weight) formatting fix, and
  accessibility click labels. Code de-duplicated with no behavioural change.

Constraints honoured: offline only; reads from Room snapshots; strictly
read-only; immutable history preserved.

Exit criteria: met — users browse history and inspect accurate immutable
details; snapshot integrity preserved; verified by 34 history-specific automated
tests (172 total). Remaining: the shared on-device pass once an AVD exists.

⸻

12. M8 — Backend Sync APIs ✅ Completed (resequenced)

Goal: implement the backend endpoints required by synchronization and the web
app, against the finalized contracts in API_SPECIFICATION.md.

Delivered across six reviewed phases:

* Phase 1 — Write foundation: JPA entities + repositories for all five remaining
  tables; enum (EnumType.STRING) and BigDecimal mappings; ResourceNotFound (404)
  and BusinessRule (409) exceptions + handlers; V4 default-user seed +
  DefaultUserProvider; PostgreSQL-backed test infrastructure.
* Phase 2 — Routine endpoints (list/detail/create/update/soft-delete/duplicate),
  idempotent create (201/200).
* Phase 3 — RoutineExercise endpoints (add/update/remove/atomic reorder).
* Phase 4 — WorkoutSession lifecycle (history/detail/start/complete/discard) with
  valid transitions, idempotent replay, and preserved client domain timestamps.
* Phase 5 — WorkoutExercise + WorkoutSet endpoints; the detail endpoint enriched
  to return the full nested snapshot (assembled in-transaction, OSIV-safe);
  workout-history immutability enforced (409 on mutation of a terminal session).
* Phase 6 — Integration hardening: end-to-end sync-graph idempotency proof,
  error-envelope consistency tests, OpenAPI/Swagger verification, documentation.

Idempotent, UUID-keyed upserts make repeated sync submissions safe. The owner is
attached server-side (single-user V1; the client sends no userId). No new Flyway
migration was needed for the tables (V1 created all eight); V4 only adds the
default-user seed row.

Exit criteria: met — all endpoints behave per the API specification, return the
correct status codes and error envelope, use DTOs, and are covered by 86 backend
tests (JUnit 5/MockMvc over real PostgreSQL), including a full-graph replay proof.

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
