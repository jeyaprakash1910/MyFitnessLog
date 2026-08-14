Project Roadmap

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: August 14, 2026

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

3. Current State Snapshot (as of August 14, 2026, v1.10.0)

This section reflects what actually exists in the repository today. It is the
authoritative status summary; a new contributor (human or AI) should be able to
orient from this plus ANDROID_ARCHITECTURE.md and ANDROID_FLOW.md alone.

Repository layout:

* backend/ — Spring Boot API (Java 21, Maven)
* android/ — Android app (Kotlin, single Gradle module)
* docs/    — architecture, database, API, sync, coding standards, ADRs
* web/     — React + TypeScript + Vite read-only history client (M10, complete)

Backend status (M1–M2, M8 Backend Sync APIs, and the M14/M15 convergence and
correction work complete):

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
  a completed workout takes set-level corrections but keeps its planning snapshot
  locked, and a discarded one is immutable (ADR-0018); the detail endpoint returns
  the full nested snapshot assembled in-transaction (OSIV disabled).
* Since v1.6.0 a client mistake returns the status it earned rather than 500:
  404 for an unknown path, 405 with an `Allow` header, 400 for a malformed UUID
  or a missing parameter, 415 for an unsupported media type (TD-001). A 500 now
  means the server genuinely failed.
* 161 automated backend tests (JUnit 5 + MockMvc) run against a real PostgreSQL
  database - including an end-to-end sync-graph idempotency proof, and coverage
  of the `X-API-Key` boundary and the exercise endpoints, both of which had none
  before v1.6.0. The backend test gap identified in M2 is now closed. (Local
  PostgreSQL is used to execute the tests; CI runs the same ones against a
  Postgres 17 service container.)
* Flyway V5 indexes WorkoutSession on (status, startedAt) for the history query;
  V6/V7 expand the seeded library to 14 categories and 313 exercises; V8 adds
  `workout_session.routine_name`.
* CORS is configured for browser clients (GET only, no credentials) so the web
  client can read the API — API_SPECIFICATION §5b.
* The default user is attached server-side; the Android client sends no userId.

Android status (M3–M7, M9, M9.5, M13–M15 complete; hardened through M11 Track A):

* Foundation: single Gradle module; Hilt DI, Jetpack Compose, Navigation Compose,
  Room, Retrofit + OkHttp + kotlinx.serialization, Material 3, version catalog,
  Gradle wrapper. Android SDK installed locally; an AVD (`mfl_test`) and a
  physical device (OnePlus CPH2717, Android 16) are both available and used.
* Room database version 7, exportSchema on (schemas v1–v7 committed):
  - Reference (download-only): ExerciseCategory, Exercise.
  - Routine templates (mutable): Routine, RoutineExercise.
  - Workout history (mutable, immutable once completed): WorkoutSession,
    WorkoutExercise, WorkoutSet.
  - Converters: UUID(String), Instant(epoch millis), SyncStatus, WorkoutStatus
    + SetCategory (by name), BigDecimal(plain string, for weight/RPE/RIR).
* Repositories: ExerciseCategoryRepository, ExerciseRepository (read-only);
  RoutineRepository, WorkoutRepository (mutable); WorkoutHistoryRepository
  (read-only, over the immutable snapshot tables). Use case: StartWorkoutUseCase.
* Implemented features (offline-first; local writes are uploaded by the
  synchronization engine, never awaited by the UI):
  - Exercise Library: initial download + local cache, list, local search,
    category filtering.
  - Routine Management: routine list (Home), routine detail, create/edit
    (rename, add/remove exercises via picker, reorder, edit targets), duplicate,
    soft-delete.
  - Workout Logging: start-from-routine (snapshot) OR manual/ad-hoc workout
    (routineId = null), or resume the active session (single-active invariant,
    atomic Room transaction); active workout screen; add exercises (manual, via
    the shared picker); add/edit/delete/toggle sets; complete/discard; a completed
    workout is closed to this screen and changeable only through the corrections
    below; workout elapsed timer (derived from startedAt) + transient rest
    countdown timer.
* Navigation: Home = routine list; nested routine detail / edit / add-exercise;
  Workout tab = active workout screen (optional routineId starts a routine
  workout; the "No active workout" state offers a manual start; else resumes the
  active one); the exercise picker is shared between routine editing and manual
  workouts; Settings holds About, the installed version and an on-demand update
  check.
  - Workout History: a history list (completed workouts only; DISCARDED and
    IN_PROGRESS excluded; newest first) led by the routine's name as snapshotted
    at start, with the date, derived duration (seconds under a minute), exercise
    count and a notes preview; a workout detail screen showing workout metadata +
    every snapshotted exercise and set (weight, reps, category, RPE), grouped and
    ordered from the snapshot. History is a real top-level destination; Workout
    Detail is a full-screen drill-down.
  - Correcting history (v1.6.0–v1.8.0, ADR-0018): from the detail screen, fix a
    set's weight/reps/RPE, add a set performed but never logged, delete one logged
    but never performed, or discard the whole workout. Each is queued like any
    other local write and uploads in the background. The planning snapshot stays
    locked and a discarded workout accepts nothing, so the detail screen is no
    longer read-only but the record still means what it says.
* 551 automated tests pass on the JVM via Robolectric (converters, DAOs,
  repositories, StartWorkoutUseCase, WorkoutClock, RestTimer, ViewModels, Compose
  UI, history and formatting helpers, and the full synchronization engine), plus
  8 instrumented tests (Hilt graph + Room migrations) which pass identically on
  the emulator and on physical hardware.
* Synchronization propagates creates, updates **and deletions** — routine and
  routine-exercise deletions travel on the soft-deleted row (M11 Phase 2); set
  deletions use a tombstone table (ADR-0007), as do workout-exercise deletions
  since Room v6 (TD-014).
* The device also reads back: restore rebuilds an empty database at launch, and
  refresh reconciles after every sync pass (ADR-0017). The Room database is
  excluded from Android Auto Backup and device transfer, so a stale cloud copy
  cannot land ahead of a real restore.
* Reference data reconciles: the local catalogue converges on the backend's,
  keeping rows still referenced by history (TD-012).
* Verified end to end on real hardware: fresh install, library download, routine
  building, workout logging, background sync under doze, and rows confirmed in
  PostgreSQL.

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
M9 Synchronization	✅ Completed (all 4 phases)
M9.5 Dogfooding Readiness	✅ Completed (T1–T4)
M10 Web Application	✅ Completed (prerequisites + Phases 1–4)
M11 Hardening & Polish	✅ Track A complete (Phases 1–4); Track B continuous
M12 Version 1 Release	✅ Completed (Phases 1–5) — v1.0.0 released 22 Jul 2026
M13 In-App Update Delivery	✅ Completed - v1.2.0 released 6 Aug 2026
M14 Backend Convergence (ADR-0017)	✅ Completed - v1.4.0 (restore) and v1.5.0 (refresh), 7 Aug 2026
M15 Editable History (ADR-0018)	✅ Completed - v1.6.0 through v1.9.0, 10-13 Aug 2026

Current status: **Version 1 released, self-updating, and converged in both
directions.** `v1.0.0` is tagged, pushed and published on GitHub (commit
`4f80e76`), with the signed release APK verified on physical hardware. Every
milestone M1–M15 is complete, the current release is **v1.10.0** (14 Aug 2026),
and the next milestone is Version 2 (authentication and multi-user), not yet
planned.

⸻

3a. Open work, as of 14 August 2026

Everything with an owner is closed, which is why this section exists: the project
has no agreed next objective, and that is now the thing holding it up rather than
any piece of code.

**The decision that blocks everything else.** No V3 roadmap is defined. This
document names Version 2 as authentication and multi-user (§18), and the V2
workout-logging index states that no new feature work is authorised until a V3
roadmap exists. Recent work has therefore been reactive polish on a finished V1.
Deciding the next objective is the highest-value thing available.

**Carried debt, all in TECH_DEBT.md.**

* **TD-018** - the debug build points at the production backend. Harmless while the
  backend holds test data, and serious from the first real training session. Fix it
  before that, not after, because nothing announces the transition.
* **TD-010** - workout history is not paginated. Real debt, invisible at present
  scale, and the M10 seams keep it a caller-side change.
* **TD-002, TD-009, TD-012** - note only, each waiting on a trigger that has not
  arrived.

**Smaller items, recorded so they are not rediscovered.**

* The launcher icon's adaptive foreground is upscaled from a 101px mark, because an
  adaptive canvas is 108dp against the legacy 48dp. Correct at launcher size, soft
  when magnified. A `VectorDrawable` built from the original artwork removes the
  limitation at every density and needs the source file.
* `RELEASE_CHECKLIST` §9 should include updating the README's version banner. It
  goes stale on every release by construction, and did so for four consecutive
  releases before being noticed on 13 August.
* The backend currently holds test data by the owner's decision, and is intended to
  be wiped once the feature set settles. Note that wiping it also clears the phone:
  `RefreshManagerImpl` deletes local completed sessions that the backend no longer
  lists, cascading to their exercises and sets. That is correct multi-device
  behaviour, and surprising if unexpected.

What the releases after V1 added, in order:

* **v1.2.0 / v1.3.0** - in-app updates over the air (M13, ADR-0016).
* **v1.4.0** - restore on a fresh install: a device with no data rebuilds itself
  from the backend at first launch. ADR-0017 Stage 1.
* **v1.5.0** - periodic refresh: a change made anywhere reaches the device within
  about 15 minutes, without overwriting an unuploaded local change or touching a
  workout in progress. ADR-0017 Stage 2. Synchronization is no longer one-way.
* **v1.6.0 / v1.6.1** - a completed workout can be corrected (ADR-0018,
  ADR-0017 Stage 3), the set-level correction reachable from the phone, plus the
  first continuous integration this repository has had and a fix for the client
  timeout that could not outlast a free-tier cold start.
* **v1.7.0** - the remaining two corrections ADR-0018 defines: add a set that was
  performed but never logged, delete one logged but never performed.
* **v1.8.0** - discard a workout that should not be in the record at all, from
  History.
* **v1.9.0** - History names a workout by its routine, recorded at the moment the
  workout starts, and a sub-minute workout reads in seconds instead of "0m".

The boundary ADR-0004 protects is unchanged throughout: the planning snapshot -
exercise name, order and targets - stays locked once a session is no longer in
progress, and a discarded workout accepts nothing. Correcting what you performed
is not the same act as rewriting what you intended.


Since **v1.2.0** an installed build updates itself over the air: the backend
resolves the latest GitHub release with a server-side read-only token and streams
its APK to the app (ADR-0016). Verified end to end on the physical OnePlus
CPH2717 on 6 Aug 2026 - a 1.2.0 install detected, downloaded and installed 1.3.0
with no cable, preserving the local database (`firstInstallTime` unchanged), and
again on 10 Aug 2026 for **1.5.0 to 1.6.0**.

That second run found a real defect first, and it took three attempts to pin down
because the obvious reading was wrong twice. The check reported "could not reach the
server" while the server was healthy: it was asleep. The free Render instance spins
down after inactivity and the first request waits for a container to boot, and the
client allowed OkHttp's default 10 seconds for everything. The update check runs
once on launch with no retry, so it was the first place this could show; background
sync had been hiding the same fault for months because WorkManager retried and the
second attempt found a warm server. Fixed in `NetworkModule` and pinned by
`OkHttpTimeoutTest`. `DEPLOYMENT.md` had predicted this and deferred it pending
evidence, which is what arrived.

Two things about that diagnosis are worth keeping, because both were nearly missed:

* **The phone had already proved its network was fine**, by downloading 8.7 MB
  through the same host minutes earlier. That single fact ruled out DNS, TLS, the
  API key and connectivity in one go, and turned "why can't the phone reach the
  server" into "what is different about *this* request". The answer was that
  nothing was: the container had gone back to sleep.
* **A failed check leaves the container booting.** The request that times out at
  10s is what starts the wake-up, and the instance is up roughly three seconds
  later. So a second attempt half a minute after the first would have succeeded,
  which is worth telling a user rather than only saying "it is warm now".

Verified working from the device on 10 Aug 2026, and 1.6.1 installed over the air
the same day. That is the remedy: the client now waits past the cold start rather
than the server being kept awake.

**A keep-alive was considered and declined on 10 Aug 2026.** Pinging the instance
every ten minutes would stop it sleeping, and Render's free tier would just cover
it (roughly 720 hours needed against 750 allowed). It was rejected because a slow
wake-up costs the user nothing: Room is the single source of truth on the device
(ADR-0002), so every screen reads locally and nothing waits on the network. The
backend is only needed for background sync, which WorkManager retries, and for
restore on a fresh install, which retries next launch. Spending the entire monthly
allowance to remove a delay nobody experiences is not a trade worth making. Revisit
only if something user-facing ever has to block on a network read, which the
offline-first design exists to prevent.
Test count: 559 automated Android tests (551 JVM/Robolectric + 8 instrumented)
+ 161 backend tests (JUnit 5/MockMvc over real PostgreSQL) + 133 web tests
(Vitest/RTL, 4 of them live-backend) = 853 total, of which 844 run by default.
All of them run on every push and pull request via GitHub Actions, the
instrumented ones on an emulator in their own job since 2026-08-07.
The nine live tests (five Android `LiveBackendSyncTest`, four web) drive the real
stack against a running backend. Since M12 Phase 3 they skip unless a target is
named explicitly (`MFL_LIVE_TEST_BASE_URL` / `VITE_LIVE_TEST_BASE_URL`) and
**fail** rather than skip if that target does not report `disposable: true`, so
no test can write to the system of record (TD-013). The instrumented tests pass identically on
the emulator and on physical hardware (Android 16).
Database version: Android Room v7 (v4 added `workout_set_tombstone` for
set-deletion propagation, ADR-0007; v5 added `exercise_category.displayOrder` so
the picker uses the catalogue's curated order; v6 added
`workout_exercise_tombstone`; v7 added `workout_session.routineName`, nullable
and deliberately not backfilled, since writing today's routine name onto older
workouts would invent a snapshot that was never taken); backend Flyway v8 (V4
seeds the default user, V5 indexes WorkoutSession on status + startedAt, V6/V7
expand the library to 14 categories and 313 exercises, V8 adds
`workout_session.routine_name` to match Room v7).
Backend: full write/read APIs per API_SPECIFICATION.md.
Sync: **bidirectional since v1.5.0** - uploads (transport, engine, scheduling,
triggers) plus restore on a fresh install and periodic refresh, in the three
stages of ADR-0017. Conflicts are last-write-wins on the server's `updatedAt`;
the download direction never overwrites a row the outbox still owns and never
touches a workout in progress. Still deferred: deletion propagation for
hard-deleted workout sets (design compared, decision pending).

Runtime validation (completed 2026-07-21): an emulator (Pixel 6, API 35) was
installed and the full loop executed against a running Spring Boot backend and
PostgreSQL — app launch, create routine in the UI, start workout, complete
workout, automatic background sync, rows confirmed in PostgreSQL, and history
rendering correctly after a fresh launch. SyncWorker was observed running in a
real process, confirming the Hilt worker-factory wiring end to end.

That run found and fixed one release-blocking defect: Android blocks cleartext
HTTP from API 28, so every sync request failed with UnknownServiceException and
synchronization could not have worked on any device (all 301 JVM tests passed
regardless — Robolectric has no network security policy). Fixed by a debug-only
network security config; release builds are unchanged.

That run also exposed a pre-existing gap unrelated to M9: nothing downloaded the
exercise library, so the picker was permanently empty on a fresh install. Both
defects are fixed in M9.5.

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

13. M9 — Synchronization ✅ Completed (all 4 phases)

Goal: synchronize Android-created data to the backend.

Delivered, by phase:

* Phase 1 — Transport: Instant/BigDecimal serializers, upload DTOs, entity→DTO
  mappers, RoutineApi / WorkoutSessionApi / WorkoutLogApi, Hilt wiring.
* Phase 2 — Engine: SyncEngine + SyncSource interfaces implemented by the
  existing repositories, pending-work queries, status-only transitions, the
  six-phase dependency-ordered upload, aggregate-isolated failure handling.
* Phase 3 — Scheduling: @HiltWorker SyncWorker, SyncScheduler, network
  constraint, exponential backoff, and stranded-claim recovery
  (SYNCING → PENDING) before every pass.
* Phase 4 — Triggers: SyncManager/SyncTrigger, app-startup scheduling, a sync
  request after every local write, and an end-to-end walkthrough.

No Room schema change was required: the syncStatus column has existed on every
mutable entity since M5/M6.

Exit criteria (met, by test): offline-created data synchronizes in dependency
order; replay is idempotent; retry works; failures are isolated to their
aggregate; no data loss. Verified by 301 JVM/Robolectric tests including an
application-level walkthrough over MockWebServer and a live-backend walkthrough
against real Spring Boot + PostgreSQL (see section 4's deferred close-out for
what remains unverified).

Deferred out of M9 (see SYNC.md §19): deletion propagation for hard-deleted
workout sets, bidirectional sync, multi-device conflict resolution, and sync
progress indicators in the UI.

Not verified: no on-device run — see the deferred close-out in section 4.

⸻

13b. M9.5 — Dogfooding Readiness ✅ Completed

Goal: make the application safe and complete enough to use daily, so that
Milestone 10 and later polish are driven by real usage rather than speculation.

Not a planned milestone. It exists because preparing to use the app in earnest
exposed defects sitting inside milestones already marked complete — the kind
only found by running the thing.

Delivered:

* T1 — Removed `fallbackToDestructiveMigration()` from Room and established a
  migration policy: every schema change now ships with a `Migration` and a
  data-preservation test. Previously the next schema bump would have silently
  deleted the entire local database, including any workout not yet synchronized.
* T2 — Fixed the exercise library. Two defects: nothing ever downloaded it (the
  only caller was a screen absent from the navigation graph), and the download
  path refreshed exercises without their categories, violating a foreign key on
  a cold database. `refreshLibrary()` now owns the ordering and the unsafe entry
  point was removed. `ExerciseListScreen` was wired in as a fifth destination.
* T3 — Made the backend URL configurable via `apiBaseUrl` in `local.properties`,
  defaulting to the emulator loopback, so syncing from a physical device is a
  configuration change rather than a source edit.
* T4 — Brought the documentation back in line with the implementation.

Exit criteria (met): a fresh install can download the exercise library, build a
routine, log a complete workout with sets, and synchronize it to PostgreSQL;
local data survives a schema change; no developer-specific configuration is
committed.

Verified on an emulator against a live backend and PostgreSQL. Not verified:
synchronization from physical Android hardware (see TECH_DEBT TD-005).

⸻

14. M10 — Web Application ✅ Completed

Prerequisites ✅ Completed 2026-07-22 — backend and sync work that had to land
before a second client could exist, done as one task ahead of any React code:

* TD-007 — CORS. `CorsConfig` permits configured browser origins, GET only, no
  credentials. Without it no web request would have succeeded at all.
* TD-003 — the history query filters and orders in SQL via a `Pageable` finder
  instead of loading every session and sorting in Java. Flyway V5 adds the
  composite index it needs (the table was never indexed on `status`).
* TD-008 — history is now defined once, on the backend: COMPLETED only. The
  endpoint previously returned DISCARDED sessions, which would have shown the
  web user workouts their phone hides.
* TD-004 — workout-set deletions reach the backend, via tombstones (ADR-0007,
  Room v4). Previously a set deleted on the phone lived on in PostgreSQL — the
  divergence would have become user-visible the moment a second client read it.

Verified end to end against a running backend and PostgreSQL, not only by test.

Goal: build the read-only web client for viewing workout history.

Delivered across four reviewed phases:

* Phase 1 — Foundation: Vite + React + TypeScript under `web/`, Tailwind, ESLint
  + Prettier, React Router, validated environment configuration, and a typed
  Axios client with centralized error normalization. Its defining decision is
  decimal-safe parsing: `weight`/`rpe`/`rir` are read as exact strings from the
  raw response text, because `JSON.parse` would silently turn `102.50` into
  `102.5` and discard the scale preserved end to end since M9.
* Phase 2 — History list: TanStack Query with hierarchical keys, loading/empty/
  error states, retry matching the API client, and formatting mirroring Android.
  Filtering and ordering are left to the backend, never re-applied client-side.
* Phase 3 — Workout detail: the full nested snapshot in a single request, sets as
  a semantic table, and Not Found for anything outside the history contract —
  matching Android's existing `WorkoutDetailUiState.NotFound`.
* Phase 4 — Responsive layout, accessibility and polish: one column from 375 px
  to 1280 px, a scrollable sets table, corrected heading hierarchy, shared focus
  styling, WCAG-AA contrast, per-route document titles, and cached `Intl`
  formatters.

Two properties are worth recording because they were measured, not assumed:
formatting output is **identical to Android's Kotlin** across 33 fixtures (run
through the real `HistoryFormatting.kt`), and decimal scale survives from a
PostgreSQL `NUMERIC` to the rendered DOM.

Depends on: M8 (endpoints) and M9 (data actually synced to the backend).

Exit criteria (met): history matches backend data, verified live (21 real
sessions, COMPLETED only, newest first); details render correctly from real
snapshots; responsive with zero horizontal overflow at 375/768/1280 px and **0
axe-core WCAG 2.1 A/AA violations**, verified in Chrome.

Not verified: Firefox/Safari, a real screen-reader pass, physical devices.
Deferred: pagination (TD-010).

⸻

15. M11 — Hardening & Polish ✅ Track A complete

Goal: improve quality and prepare for release, driven by real usage.

Reframed after M9.5, then split during planning into two tracks, because the
milestone is defined as usage-driven but had no usage data when it began:

* **Track A** — work identifiable up front. Closeable, and now closed.
* **Track B** — whatever real use surfaces. Continuous; carries into M12 as
  ordinary maintenance rather than blocking the milestone.

Track A delivered across four reviewed phases:

* Phase 1 — Physical-device verification (TD-005). Full core-flow pass on a
  OnePlus CPH2717 (Android 16): migration on real data, library download, sync to
  PostgreSQL, doze deferral, ColorOS background policy, and scroll performance
  with 313 exercises. Found three defects, fixed none of them — deliberately, so
  the pattern between them could be seen.
* Phase 2 — Synchronization correctness. Routine and routine-exercise deletions
  never reached the backend (TD-011): the pending query excluded soft-deleted
  rows and the engine had no delete dispatch. Fixed by making the existing
  pipeline look at the row it already had, **without** adding a third
  synchronization mechanism. Reference data now reconciles removals (TD-012), and
  `exercise_category.displayOrder` reaches the client (Room v5, plus an additive
  API field). Verified on the user's own data: device and backend converged.
* Phase 3 — Test consolidation. Audited the suite for tests encoding
  implementation rather than requirements; the flaky-test concern was closed with
  evidence (12 runs, 3 orderings). Found and fixed a production bug — an empty
  catalogue response would have wiped the local library — and closed the
  structural gap that let TD-011 survive: the end-to-end walkthrough had no
  deletion coverage at all.
* Phase 4 — Workflow hardening. Made the device-safety rule structural rather
  than documentary, and brought the documentation back in line with the
  implementation.

Deliberately deferred, with reasons recorded in TECH_DEBT and the phase journals:

* Web pagination (TD-010) — real debt, invisible at present scale, and the M10
  seams keep it a caller-side change.
* Cross-browser and screen-reader passes for the web client — neither blocks
  daily use; both matter before anyone else opens the app.
* Long-idle ColorOS background behaviour — needs elapsed time, not a command.
* Local purge of soft-deleted synced rows — deleting local data needs a safety
  argument first.

Exit criteria (met): critical bugs resolved; core workflows verified on real
hardware; documentation matches implementation.

⸻

16. M12 — Version 1 Release ✅ Completed

Goal: release Version 1 as a signed, verified, reproducible artifact.

Delivered across five reviewed phases:

* Phase 1 — Release build, signing and versioning (TD-006). Signing credentials
  outside version control; `versionCode` derived from a single `versionName` so
  it cannot be forgotten; release cleartext scoped to the one configured host and
  removing itself once that URL becomes HTTPS. The release variant had never been
  built before — four defects surfaced the first time it was.
* Phase 2 — Physical-device verification and data protection. Signed APK
  installed on a OnePlus CPH2717 (Android 16) after a verified backup; full
  workflow confirmed in PostgreSQL. Found that the Room database file was 4 KB
  while its WAL held 272 KB, so a naive backup would have been empty.
* Phase 3 — Environment isolation (TD-013). Automated tests were writing into the
  system of record. Fixed by having the backend declare itself disposable rather
  than having clients guess from reachability.
* Phase 4 — Documentation audit and repository readiness. Claims executed rather
  than read; found stale version ranges, a stale test count, a leaked LAN address,
  missing web build instructions, and a `.gitignore` rule that made the web client
  unconfigurable from a fresh clone.
* Phase 5 — Release and closure. `v1.0.0` tagged, pushed and published.

Release checklist (all met):

* ✅ All milestones completed.
* ✅ All APIs documented — including the health `disposable` field (Phase 3).
* ✅ Database migrations verified — Room v1→v5, Flyway V1→V7.
* ✅ Android application stable — verified on physical hardware from the release
  build, not the debug build.
* ✅ Backend stable — restarted post-release; `/health` returns
  `{"status":"UP","disposable":false}`, matching the documented contract.
* ✅ Web application stable — 133 tests, zero WCAG 2.1 A/AA violations.
* ✅ Documentation updated — audited by execution in Phase 4.
* ✅ GitHub repository organized — no secret, credential or artifact has ever
  been committed, verified across the whole history.
* ✅ Release tag created — `v1.0.0` → `4f80e76`, published at
  https://github.com/jeyaprakash1910/MyFitnessLog/releases/tag/v1.0.0

Post-release, all closed:

* ✅ Signing keystore backed up to three locations, with the backup's fingerprint
  matched against the signer of the released APK — proof it can still sign an
  update, not merely that a file was copied.
* ✅ Fresh PostgreSQL dump taken and verified by restoring it.
* ✅ 71 historical test routines removed with their cascade; zero orphans.
* ✅ Signed APK archived with its digest, signer digest and tagged commit.

Release notes: docs/V1_RELEASE_NOTES.md. Procedure: docs/RELEASE_CHECKLIST.md.

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

Version 1 is considered complete when all of the following hold. Status as of
2026-08-13 (v1.9.0); each was already met at the v1.0.0 release on 2026-07-22,
and the notes record where a later release strengthened one:

* ✅ Users can create workout routines.
      Verified on an emulator: routine created, exercises added with targets.
* ✅ Users can perform complete workouts offline.
      Achieved in M9.5 (T2). Previously blocked — the exercise picker was
      permanently empty on a fresh install, so no exercise could be added to any
      workout. Now verified end to end on a fresh install: library downloaded,
      exercise added, set logged, workout completed.
* ✅ Workout history is permanently stored.
      Room is the on-device record (durable across restarts, and no longer at
      risk from a destructive migration after T1); the backend holds the
      permanent copy in PostgreSQL, and since v1.4.0 that copy can repopulate a
      device that lost its own. Since v1.6.0 a stored workout can also be
      corrected without the record ceasing to mean what it says (ADR-0018).
* ✅ Android synchronizes with the backend automatically.
      SyncWorker observed running in a real process and rows confirmed in
      PostgreSQL. One-way (Android → backend) at v1.0.0; bidirectional since
      v1.5.0, so a device also rebuilds itself from the backend after a reinstall
      (v1.4.0) and picks up changes made elsewhere (v1.5.0).
* ✅ Workout history is viewable on the web.
      Achieved in M10. Verified against real synchronized data: 21 sessions
      rendered from PostgreSQL through the live backend, with workout detail,
      responsive layout and zero WCAG 2.1 A/AA violations.
* ✅ All documentation is consistent with the implementation.
      Audited in M12 Phase 4 by sampling claims against behaviour rather than
      reading for plausibility. That found and fixed genuine errors — a wrong
      Flyway range and backend test count in the README, a wrong migration range
      in the release checklist, a live-test description obsoleted by M12 Phase 3,
      and a developer's LAN address in a public example. It also found two
      omissions: the web client had no build instructions, and `.gitignore`
      excluded `web/.env.example`, so the web app could not be configured from a
      fresh clone. Requires maintenance, as always; the checklist now makes
      sampling a release gate.
* ✅ The application is stable, maintainable, and ready for future expansion.
      All three blockers named here are resolved: deletion propagation (TD-004,
      TD-011), physical-device verification (TD-005) and release signing
      (TD-006). TD-013 — automated tests writing to the system of record — was
      found and fixed during M12. What remains open in TECH_DEBT.md is
      note-only or deliberately deferred (TD-001, TD-002, TD-009, TD-010,
      TD-012); none blocks V1.

Explicitly out of scope for V1 (see §18): authentication and multi-user support
are Version 2. V1 attaches a single default user server-side, so a V1 release
means personal or trusted-group use on a private backend, not public
distribution.
