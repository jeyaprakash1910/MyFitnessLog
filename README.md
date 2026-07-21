# MyFitnessLog

An **offline-first** workout tracking application. The backend is the permanent
source of truth; the Android app keeps a synchronized local copy so it works
fully offline.

> Status (July 21, 2026): Android client complete (exercise library, routine
> management, workout logging with timers, and read-only workout history); the
> backend implements the full write/read REST contract with idempotent sync
> semantics; **one-way background synchronization (Android → backend) is
> implemented and tested (M9)**. Next milestone: the read-only web client (M10).
> See [docs/ROADMAP.md](docs/ROADMAP.md) for the authoritative status.

## Repository layout

```
backend/   Spring Boot REST API (Java 21, Maven, PostgreSQL, Flyway)   — M1–M2, M8 done
android/   Android app (Kotlin, Compose, Room, Hilt, Retrofit, WorkManager) — M3–M7, M9 done
web/       React read-only history client                              — not started (M10)
docs/      Product, architecture, database, API, sync, coding standards, ADRs
```

## What works today

- **Backend:** the full REST contract over a Flyway-managed PostgreSQL schema —
  read-only exercise/category APIs plus write/read APIs for routines (CRUD +
  duplicate), routine exercises (add/update/remove/reorder), and workout sessions
  (history/detail/start/complete/discard) with their exercises and sets. Idempotent
  UUID-keyed upserts (201 create / 200 replay), valid status transitions (409 on
  illegal), immutable completed/discarded workouts, and a nested workout-detail
  snapshot. The single V1 user is attached server-side. Interactive docs at
  `/swagger-ui/index.html`.
- **Android (offline-first, Room is the source of truth):**
  - Exercise library: download + local cache, list, local search, category filter.
  - Routine management: list, detail, create/edit (add/remove/reorder exercises,
    edit targets), duplicate, soft-delete.
  - Workout logging: start/resume a workout from a routine (immutable snapshot of
    the routine) **or a manual/ad-hoc workout** (no routine); log sets,
    complete/discard, workout + rest timers. Completed workouts are immutable.
  - Workout history: read-only list of completed workouts (date, derived
    duration, routine/manual indicator, exercise count, notes preview) and a
    read-only detail screen (metadata + every snapshotted exercise and set),
    served by a dedicated read-only repository over the snapshot tables.
  - Background synchronization: every local write is uploaded to the backend in
    dependency order by a WorkManager-scheduled pass — offline-first throughout
    (the UI never waits on the network), with idempotent replay, exponential
    backoff, failure isolation per aggregate, and recovery of work stranded by
    process death.
- **301 automated Android tests** (JVM/Robolectric) + **86 backend tests**
  (JUnit 5/MockMvc over real PostgreSQL, incl. an end-to-end sync-graph
  idempotency proof) pass — 387 total. Two of the Android tests drive the real
  sync stack against a running backend and skip automatically when none is
  reachable.

Not yet built: the web client (M10), bidirectional/pull synchronization, and
deletion propagation for hard-deleted workout sets. See the roadmap.

## Documentation (read these first)

| Doc | Purpose |
| --- | --- |
| [docs/ROADMAP.md](docs/ROADMAP.md) | **Start here** — current status, milestones, phase-by-phase progress |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System-wide architecture (all three apps) |
| [docs/ANDROID_ARCHITECTURE.md](docs/ANDROID_ARCHITECTURE.md) | Android architecture, conventions, and the *why* behind decisions |
| [docs/ANDROID_FLOW.md](docs/ANDROID_FLOW.md) | Screen-by-screen app flow + implementation status |
| [docs/DATABASE.md](docs/DATABASE.md) | Canonical relational data model |
| [docs/API_SPECIFICATION.md](docs/API_SPECIFICATION.md) | REST API contract (frozen) |
| [docs/SYNC.md](docs/SYNC.md) | Synchronization strategy and the implemented upload contract |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | Coding conventions |
| [docs/ADR/](docs/ADR/) | Architecture Decision Records |

## Development philosophy

Documentation-first, KISS, YAGNI, one small reviewable unit at a time, and
**runtime verification after every unit** (build + execute tests before a unit is
considered done). Build vertically; keep the app runnable at every milestone.

## Building & testing

### Backend
Requires JDK 21 and a local PostgreSQL (a `myfitnesslog` database for the app and
a `myfitnesslog_test` database for tests).
```bash
cd backend
mvn spring-boot:run   # Flyway migrates (V1–V4) on start; API at :8080/api/v1
mvn test              # 86 tests (JUnit 5 + MockMvc) against the test database
```
Tests run against a real PostgreSQL (faithful to the quoted-identifier schema and
CHECK constraints). They are portable to Testcontainers on a Docker-capable machine
via the `TEST_DB_*` env overrides in `src/test/resources/application.yml`.

### Android
Requires JDK 17 and the Android SDK (`ANDROID_HOME` / `local.properties`).
```bash
cd android
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run all unit tests (JVM/Robolectric)
```
No emulator/AVD is currently configured, so on-device verification is pending;
all layers are otherwise covered by executed tests.

## Key architectural decisions

- **Offline-first:** the UI observes Room via Flow and never calls the network
  directly (ADR-0002); the backend is the permanent source of truth (ADR-0003).
- **UUIDv4 generated on-device**, UTC millisecond timestamps, `BigDecimal` for
  weight/RPE/RIR to stay exact with the backend.
- **Immutable, snapshot-based workout history:** starting a workout copies the
  routine's exercises so later routine edits never change past workouts.
- **Feature-first packages, single Gradle module, MVVM + Repository**, focused
  use cases only where justified (e.g. `StartWorkoutUseCase`).
