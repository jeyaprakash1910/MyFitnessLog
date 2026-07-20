Android Architecture

Project: MyFitnessLog
Version: 1.0
Status: Approved (Milestone 3 baseline)
Last Updated: July 20, 2026

⸻

1. Purpose

This document records the approved architecture for the MyFitnessLog Android
application. It is the baseline for all Android implementation work and reflects
the Milestone 3 architecture review and its accepted refinements.

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
    sync/                 (Milestone 8)

Feature packages lift cleanly into Gradle modules later if needed.

⸻

5. Dependency Injection

- Hilt. Chosen for compile-time graph validation and first-class WorkManager
  integration (needed in Milestone 8).
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
  added when each entity is first created — not retrofitted at Milestone 8.
  Reference data (Exercise, ExerciseCategory) is download-only and needs no
  sync status.

Note: Room requires at least one entity to compile a `@Database`. Until the
first entity exists (Milestone 4 / Phase 2), the database class is a documented
placeholder while the surrounding Room infrastructure is fully configured.

⸻

6a. Repository API-Shape Convention (accepted refinement)

All repositories follow a consistent public API shape so that consistency scales
as repositories multiply.

Read-only (reference-data) repositories expose exactly:

- observeAll(): Flow<List<Entity>>
- observeById(id): Flow<Entity?>
- refresh()   // download from backend, upsert into Room

Mutable (user-data) repositories, when introduced, extend this with the write
and sync operations they need (e.g. insert/update/delete plus a sync trigger).
Exact naming is settled when the first mutable repository is built; the point is
that every repository of the same kind presents the same surface.

Reads are always Room-backed Flows; refresh/sync are suspend functions. Errors
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
- suspend functions for one-shot writes/fetches.
- Background sync is owned by WorkManager, never viewModelScope.
- Repository boundary returns a sealed result type; network/server errors during
  sync are handled by ret/retry and never surface as UI failures. Empty local
  database is a normal Loading/Empty state, not an error.

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

- Unit (JVM): ViewModel tests (fake Repository, TestDispatcher), mapper tests,
  converter tests.
- Repository tests, including repository contract tests that assert every
  implementation satisfies its interface regardless of backing changes.
- Room DAO tests against an in-memory database (instrumented).
- Compose UI tests per screen as screens land.

⸻

12. Intentionally Not Built in Version 1

- Multi-module Gradle setup.
- Separate domain module / DTO→Entity→Domain triple mapping.
- A global UseCase / Interactor layer.
- A LocalDataSource wrapper around DAOs.
- Any sync engine, WorkManager jobs, or network calls before their milestone.
