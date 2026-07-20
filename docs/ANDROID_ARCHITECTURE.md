Android Architecture

Project: MyFitnessLog
Version: 1.0
Status: Approved — reflects Milestone 7 (complete)
Last Updated: July 22, 2026

⸻

1. Purpose

This document records the approved architecture for the MyFitnessLog Android
application and is the single source of truth for how the Android app is built.
It reflects everything implemented through Milestone 7 (exercise library, routine
management, workout logging — routine-based and manual — with timers, and
read-only workout history). Sections 13–17 describe the workout domain added in
Milestones 5–6; section 18 describes the workout history feature added in
Milestone 7.

It complements, and does not override, the system-wide documents: PRD.md,
ARCHITECTURE.md, DATABASE.md, API_SPECIFICATION.md, ANDROID_FLOW.md, SYNC.md,
and CODING_STANDARDS.md.

⸻

2. Fixed Constraints (from existing documentation)

- Room is the single source of truth on Android (ADR-0002).
- The backend is the permanent source of truth (ADR-0003).
- The UI observes Room via Kotlin Flow and never calls Retrofit directly.
- All identifiers are UUIDv4, generated on-device (DATABASE.md).
- All timestamps are UTC with millisecond precision (DATABASE.md).
- One-way synchronization (Android → backend) in Version 1 (SYNC.md).

⸻

3. Architecture Style

- Pattern: MVVM + Repository.
- Layers: Presentation (Compose + ViewModel) → Repository → Data (Room DAO,
  Retrofit API). Two real layers with an optional thin domain touch-point.
- Clean Architecture: adopted in spirit (dependency rule inward), not in full.
  No separate domain module.
- UseCases: NOT introduced globally. Add a UseCase only when a specific piece of
  business logic is non-trivial or shared across ViewModels (e.g. snapshotting a
  routine into a workout). Incremental, never blanket.

⸻

4. Module & Package Structure

- Single Gradle module for Version 1 (YAGNI; revisit only under real scaling
  pressure).
- Feature-first packaging with a thin shared `core` layer:

com.myfitnesslog
    core/
        data/local        (Room database, converters)
        data/remote       (Retrofit/OkHttp setup)
        di                (app-wide Hilt modules)
        ui/theme, ui/navigation
        model             (RESERVED — see section 9; do not create while empty)
        util
    feature/
        exercise, routine, workout, history, settings
    sync/                 (Milestone 9 — Synchronization)

Feature packages lift cleanly into Gradle modules later if needed.

⸻

5. Dependency Injection

- Hilt. Chosen for compile-time graph validation and first-class WorkManager
  integration (needed in Milestone 9, Synchronization).
- App-wide modules live in `core/di`: DatabaseModule, NetworkModule,
  DispatcherModule.
- Dispatchers are injected behind qualifiers so they can be overridden in tests.

⸻

6. Persistence (Room)

- Entities are named `<Name>Entity` and map to the DATABASE.md model verbatim.
- UUID stored as String; Instant stored as epoch milliseconds — via
  TypeConverters.
- Reads return Flow; writes are suspend functions.
- `exportSchema = true`; exported schemas are committed to Git so the local
  database has the same migration discipline Flyway provides on the backend.
- Mutable entities carry a `syncStatus` column (PENDING/SYNCING/SYNCED/FAILED),
  added when each entity is first created — not retrofitted at sync time.
  Reference data (Exercise, ExerciseCategory) is download-only and needs no
  sync status.
- Enums (WorkoutStatus, SetCategory) are stored by name. Money/precision values
  (weight, RPE, RIR) use BigDecimal stored as a plain string — see section 16.

Current schema: database version 3, exportSchema on (schemas v1/v2/v3 committed).
Entities: ExerciseCategory, Exercise (reference); Routine, RoutineExercise
(templates); WorkoutSession, WorkoutExercise, WorkoutSet (history). Pre-release
uses `fallbackToDestructiveMigration()`; real Migrations begin once shipped
(reference data re-downloads, and no user data has shipped yet).

⸻

6a. Repository API-Shape Convention (accepted refinement)

All repositories follow a consistent public API shape so that consistency scales
as repositories multiply.

Read-only (reference-data) repositories expose exactly:

- observeAll(): Flow<List<Entity>>
- observeById(id): Flow<Entity?>
- refresh()   // download from backend, upsert into Room

A second, distinct read-only shape exists for immutable history (WorkoutHistory
Repository, Milestone 7): it exposes only `observe*` Flows over already-persisted
snapshot rows and has NO `refresh()` and NO writes — the data is produced by the
workout feature and never fetched or mutated here. See section 18.

Mutable (user-data) repositories follow this shape (established by
RoutineRepository, the first mutable repository, in Milestone 5):

- observe*  : Room-backed Flow reads the UI collects (the source of truth).
- write ops : suspend functions (create/rename/delete/duplicate/add/remove/
              update/reorder, as the feature needs) that persist locally and
              mark affected rows PENDING for later sync. They never call the
              network.
- new records use on-device UUIDv4; createdAt/updatedAt come from an injected
  Clock (so time is deterministic in tests); soft-delete via an isDeleted flag.
- write operations that change data set syncStatus = PENDING so the future sync
  layer (Milestone 9) can find and upload them.

Reads are always Room-backed Flows; write/sync are suspend functions. Errors
propagate by throwing until a presentation-layer consumer needs a structured
result (see section 8).

⸻

6b. Feature Screen Structure Convention (accepted refinement)

Every feature screen follows the same file layout so features are organized
identically and stay discoverable as the app grows:

- <Feature>ViewModel.kt   — Hilt ViewModel, owns state + events
- <Feature>UiState.kt     — immutable UI state (+ small presentation models)
- <Feature>Screen.kt      — route composable: pulls the ViewModel, collects
                            state lifecycle-aware, delegates to the content
- <Feature>Content.kt     — stateless rendering of the UI state (previewable,
                            directly testable); may live in the Screen file for
                            small screens, split out as it grows

Reads flow ViewModel → StateFlow<UiState> → Screen → Content. The stateless
content never touches the ViewModel, DI, or lifecycle.

⸻

7. Networking (Retrofit)

- Retrofit + OkHttp with kotlinx.serialization (single chosen JSON library —
  Kotlin-first, reflection-free).
- One API interface per backend resource group, colocated in each feature.
- DTOs are separate from Room entities; hand-written mapper functions convert
  DTO → Entity in the repository.
- JSON parsing tolerates unknown keys (additive backend changes).
- Debug-only HTTP logging, gated by a BuildConfig flag.

⸻

8. Coroutines, Flow, Error Handling

- Flow for all UI read paths (DAO → Repository → StateFlow<UiState>).
- suspend functions for one-shot writes/fetches; write work runs on an injected
  @IoDispatcher.
- Background sync will be owned by WorkManager (Milestone 9), never viewModelScope.
- Current error strategy: repositories throw (e.g. IllegalStateException when a
  completed workout is mutated; SQLiteConstraintException on a bad FK). A
  structured Result/sealed error type is intentionally NOT built yet — it is
  added only when a presentation consumer needs to render distinct error states.
  Today ViewModels wrap risky writes in runCatching so a rejected write never
  crashes the UI. Empty local data is a normal Loading/Empty state, not an error.
- Offline-first state precedence: when cached data exists it is shown (Success)
  even if a refresh failed; Error/Empty surface only when there is nothing to
  show. Filtering/searching never triggers the network.

⸻

9. Entity Purity Rule (accepted refinement)

Room entities MUST remain persistence models only.

The following must NOT appear inside Room entities:
- formatted dates or display strings
- UI colors or presentation flags
- formatting or presentation helpers

Presentation concerns belong in UI models. Keeping entities clean preserves the
option of introducing a dedicated domain model later without a painful refactor.

⸻

10. Reserved: core/model (accepted refinement)

`core/model/` is reserved for lightweight shared models that are NOT persistence
models and NOT a domain layer — for example UiMessage, LoadingState,
SyncStatusUi, or a Result wrapper.

Do not create the package while it would be empty; create it when the first such
model is genuinely needed.

⸻

11. Testing Strategy

- All tests run on the JVM via Robolectric (no emulator is installed). Room DAO,
  repository, ViewModel, and Compose UI tests all execute this way — including
  real in-memory Room databases, which give higher confidence than mocking.
- Timer/business logic (WorkoutClock, RestTimer) is extracted into pure/
  scope-driven classes and unit-tested with plain JUnit and virtual time.
- ViewModel integration tests use the real repository over in-memory Room and
  await real Flow emissions (a shared `awaitFirst` helper) rather than virtual
  time, because Room emits on background threads.
- Current count: 172 passing tests. On-device verification is the one deferred
  gap (no AVD); every layer is otherwise covered by executed tests.
- JUnit4 gotcha: a Kotlin `@Test`/`@Before` whose last expression returns a value
  (e.g. ends in `addExercise(...)` or `assertThrows`) is not `void` and JUnit
  rejects the class — add a trailing `Unit`.

⸻

12. Intentionally Not Built (Version 1)

- Multi-module Gradle setup.
- Separate domain module / DTO→Entity→Domain triple mapping.
- A global UseCase / Interactor layer (only focused use cases — see section 14).
- A LocalDataSource wrapper around DAOs.
- Any sync engine, WorkManager jobs, or network calls before their milestone.
- Foreground services, notifications, or alarms for timers (timers are in-VM only).

⸻

13. Workout Snapshot Architecture (Milestone 6)

Workout history is immutable and snapshot-based — the defining rule of the app.
When a routine workout starts, each RoutineExercise is COPIED into a
WorkoutExercise row carrying the exercise name and all planned targets. Completed
workouts therefore never change when the routine is later edited, renamed,
reordered, or deleted. Manual workouts use the same semantics: when the user adds
an exercise, the master exercise NAME is snapshotted onto the WorkoutExercise (so
a later exercise rename does not alter the workout).

Why snapshots (not references): a routine is a mutable template; workout history
is a permanent record of what actually happened. Referencing live routine rows
would corrupt history on any future edit. This is verified by a test that edits/
removes the routine after starting and asserts the snapshot is unchanged.

History tables (WorkoutSession/Exercise/Set) are NEVER soft-deleted; a discarded
workout is kept with status = DISCARDED (hidden from the user), not deleted.

⸻

14. Workout Lifecycle, Invariants, and StartWorkoutUseCase

- WorkoutStatus: IN_PROGRESS → COMPLETED or DISCARDED (once, terminal).
- Single active session invariant: at most one IN_PROGRESS workout exists.
  Starting resumes the active session instead of creating a second. Enforced at
  a single point — StartWorkoutUseCase — because it is the only creator of
  sessions; the repository never creates one.
- StartWorkoutUseCase is a focused use case (the first non-trivial business rule,
  exactly the case section 3 reserves a UseCase for — not a blanket layer). Its
  signature is `invoke(routineId: UUID?)`: it resolves/resumes the active session,
  else creates a session and, if a routineId is given, snapshots the routine's
  exercises — all inside a single Room `withTransaction` so a failure (e.g. an
  invalid routine FK) rolls back and leaves no partial workout.
- Manual (ad-hoc) workouts are the SAME workflow with `routineId = null`: the
  session is created with no exercises, and the user adds them during the workout
  via WorkoutRepository.addExercise(sessionId, exerciseId, exerciseName). There is
  ONE workout domain — routine and manual workouts share the use case, repository,
  ViewModel, screen, and picker; no parallel implementations exist.
- Completed-workout immutability: WorkoutRepository checks the owning session is
  IN_PROGRESS before any exercise/set add/update/delete and before complete/
  discard; otherwise it throws IllegalStateException. The UI derives read-only
  from status (`isReadOnly = status != IN_PROGRESS`) and hides mutating controls.
- setNumber / exerciseOrder are auto-assigned; timestamps come from the injected
  Clock; value guards (weight ≥ 0, reps ≥ 0, RPE 1..10) live in the repository
  (Room has no CHECK constraints; the backend remains authoritative).

⸻

15. Timer Architecture (Milestone 6 Phase 4)

- Workout elapsed time is DERIVED, never stored: WorkoutClock.elapsed(startedAt,
  endedAt, now) is a pure function. The ViewModel recomputes it each second from
  the session + injected Clock. It freezes when endedAt is set (workout ended)
  and reconstructs after process death because it depends only on persisted data.
- Rest countdown is transient UI state: RestTimer is a Compose-independent class
  driven by an injected CoroutineScope and injectable tick interval, exposing a
  StateFlow of Idle/Running/Finished with start/restart/cancel/skip. It is not
  persisted, synchronized, or backed by a service — losing it on process death
  is acceptable for V1.
- Both are unit-tested independently (pure function; virtual-time countdown).

⸻

16. BigDecimal & Value Handling

Weight, RPE, and RIR use java.math.BigDecimal (not Double) to match the backend
DECIMAL columns exactly and avoid binary floating-point drift. Stored via a
converter as `toPlainString()`, preserving value and scale, verified by
scale-sensitive round-trip tests. This keeps Android and PostgreSQL aligned
before synchronization is introduced.

⸻

17. Navigation Conventions

- Single Activity + Navigation Compose. Home hosts the routine list (ANDROID_FLOW
  defines Home as routines). Top-level destinations (Home/Workout/History/
  Settings) keep the bottom navigation bar.
- Drill-down destinations (routine detail/edit/add-exercise) render full-screen:
  the bottom bar is hidden and the top bar shows a back arrow.
- Feature routes live in the feature (e.g. RoutineRoutes, WorkoutRoutes); the app
  NavHost references them. The Workout route is parameterised
  (`workout?routineId={routineId}`): with a routineId it starts a workout from a
  routine, without one the tab resumes the active session. Top-level detection
  compares the base route (before "?") so parameterised top-level routes still
  register as top-level.
- One-shot navigation (open editor after create; finish → History; discard →
  Home) is delivered via a ViewModel SharedFlow of events the screen collects.
- Shared exercise picker: a single ExercisePicker screen/ViewModel serves both
  "add exercise to a routine" and "add exercise to an active manual workout",
  selected by which nav argument is present (routineId vs workoutSessionId). It
  adds to the correct target and pops back; Room reactivity updates the origin
  screen. The picker currently lives in the routine feature and has a small,
  accepted cross-feature dependency on the workout repository/routes.
- Manual workout entry: the Workout tab's "No active workout" state offers a
  manual start (WorkoutViewModel.startManualWorkout → StartWorkoutUseCase(null));
  the active workout screen offers "Add exercise" → the shared picker route
  `workout/add-exercise/{workoutSessionId}`.
- Workout History (Milestone 7): History is the top-level `history` destination
  (keeps the bottom bar); Workout Detail is a full-screen drill-down
  `history/{sessionId}` (bottom bar hidden, back arrow). Routes live in
  WorkoutHistoryRoutes; the app NavHost references them. Detail uses standard
  back navigation only — no deep links.

⸻

18. Workout History Architecture (Milestone 7)

Workout history is a READ-ONLY view over the immutable snapshot tables the
workout feature already writes (WorkoutSession/WorkoutExercise/WorkoutSet). It
adds no tables, columns, or schema version — the database stays at v3.

Why a separate `feature/history` package and repository (not reuse the workout
repository or DAOs):

- Read concerns differ from write concerns. The workout feature owns the active
  workout lifecycle, mutations, and business rules (single-active invariant,
  completed-workout immutability). History only reads finished workouts for
  display. Keeping them apart stops the workout DAOs from drifting into "god
  DAOs" and keeps each feature's surface focused.
- WorkoutHistoryRepository is read-only by construction: it exposes only
  `observe*` Flows and has no write methods, so there is no accidental mutation
  path into history — enforcing the product guarantee that completed workouts are
  historical records, not editable documents. (The one place history can change
  is the future "workout edit" correction flow, which is out of Version 1 scope.)

Components:

- WorkoutHistoryDao — a dedicated read-only DAO querying the existing snapshot
  tables. It surfaces COMPLETED sessions only (DISCARDED and IN_PROGRESS are
  hidden), newest first by `startedAt`. A grouped summary query
  (`observeCompletedSummaries`, LEFT JOIN + COUNT) supplies the list's exercise
  count in one query instead of an N+1 of per-session reads.
- WorkoutHistoryRepository / Impl — a thin read-only pass-through over the DAO
  (no timestamps, no syncStatus, no writes), returning the existing Room entities
  behind the feature boundary.
- Presentation follows the standard Screen → Content → UiState → ViewModel shape:
  * WorkoutHistoryViewModel maps completed workouts into fully-formatted list
    items; WorkoutDetailViewModel combines a completed session, its exercises,
    and all its sets (grouping sets under each exercise in captured order) into an
    immutable detail state, resolving anything non-COMPLETED to NotFound.
  * HistoryFormatting holds the pure presentation helpers (date, derived
    duration, routine/manual label, weight×reps, set-category label, RPE, notes
    sanitisation). All derived values (notably workout duration) are computed
    here from persisted timestamps and never stored — consistent with the Entity
    Purity rule (§9) and the derived-timer approach (§15). Duration reuses
    WorkoutClock, so the single elapsed-time definition serves both the live
    timer and history.

Snapshot integrity: because history reads the WorkoutExercise/WorkoutSet
snapshots (not live routine/exercise rows), later edits to routines or master
exercises never alter a past workout — the defining rule of the app (§13) holds
end-to-end through the history UI.
