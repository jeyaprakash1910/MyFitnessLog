# MyFitnessLog

An **offline-first** workout tracking application. The backend is the permanent
source of truth; the Android app keeps a synchronized local copy so it works
fully offline.

> Status (July 22, 2026): Backend exercise library complete; Android exercise
> library, routine management, workout logging (routine-based and manual, with
> timers), and workout history (read-only list + detail) complete. Next
> milestone: Backend Sync APIs (M8).
> See [docs/ROADMAP.md](docs/ROADMAP.md) for the authoritative, up-to-date status.

## Repository layout

```
backend/   Spring Boot REST API (Java 21, Maven, PostgreSQL, Flyway)   — M1–M2 done
android/   Android app (Kotlin, Jetpack Compose, Room, Hilt, Retrofit)  — M3–M7 done
web/       React read-only history client                              — not started
docs/      Product, architecture, database, API, sync, coding standards, ADRs
```

## What works today

- **Backend:** exercise library read APIs (`/exercise-categories`, `/exercises`,
  `/exercises/{id}`, `/exercises/search`) over a Flyway-managed PostgreSQL schema
  (all 8 tables exist; only exercise/category logic is implemented). Read-only.
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
- **172 automated Android tests** pass on the JVM via Robolectric.

Not yet built: backend write APIs (M8), synchronization / WorkManager (M9), the
web client (M10). See the roadmap.

## Documentation (read these first)

| Doc | Purpose |
| --- | --- |
| [docs/ROADMAP.md](docs/ROADMAP.md) | **Start here** — current status, milestones, phase-by-phase progress |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System-wide architecture (all three apps) |
| [docs/ANDROID_ARCHITECTURE.md](docs/ANDROID_ARCHITECTURE.md) | Android architecture, conventions, and the *why* behind decisions |
| [docs/ANDROID_FLOW.md](docs/ANDROID_FLOW.md) | Screen-by-screen app flow + implementation status |
| [docs/DATABASE.md](docs/DATABASE.md) | Canonical relational data model |
| [docs/API_SPECIFICATION.md](docs/API_SPECIFICATION.md) | REST API contract (frozen) |
| [docs/SYNC.md](docs/SYNC.md) | Synchronization strategy (design; not yet implemented) |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | Coding conventions |
| [docs/ADR/](docs/ADR/) | Architecture Decision Records |

## Development philosophy

Documentation-first, KISS, YAGNI, one small reviewable unit at a time, and
**runtime verification after every unit** (build + execute tests before a unit is
considered done). Build vertically; keep the app runnable at every milestone.

## Building & testing

### Backend
```bash
cd backend
./mvnw spring-boot:run      # requires a local PostgreSQL; Flyway migrates on start
./mvnw test                 # (no backend tests yet — see roadmap)
```

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
