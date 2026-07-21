# MyFitnessLog

An **offline-first** workout tracking application. The backend is the permanent
source of truth; the Android app keeps a synchronized local copy so it works
fully offline.

> Status (July 22, 2026): the Android app is **usable day to day** — build
> routines, log workouts offline, and have them synchronize to the backend
> automatically in the background (M9), verified end to end on an emulator
> against a live backend and PostgreSQL. The backend implements the full
> write/read REST contract with idempotent sync semantics. Next milestone: the
> read-only web client (M10).
> See [docs/ROADMAP.md](docs/ROADMAP.md) for the authoritative status and
> [docs/TECH_DEBT.md](docs/TECH_DEBT.md) for known limitations — notably that
> there is no signed release build yet, and no authentication (V2).

## Repository layout

```
backend/   Spring Boot REST API (Java 21, Maven, PostgreSQL, Flyway)   — M1–M2, M8 done
android/   Android app (Kotlin, Compose, Room, Hilt, Retrofit, WorkManager) — M3–M7, M9, M9.5 done
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
  - Exercise library: its own tab — download + local cache, list, local search,
    category filter. Downloaded on demand (categories then exercises, in that
    order — exercises reference categories by foreign key).
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
- **309 automated Android tests** (305 JVM/Robolectric + 4 instrumented, the
  latter needing an emulator) + **86 backend tests** (JUnit 5/MockMvc over real
  PostgreSQL, incl. an end-to-end sync-graph idempotency proof) pass — 395
  total. Two of the Android tests drive the real sync stack against a running
  backend and skip automatically when none is reachable.

Not yet built: the web client (M10), authentication (V2), bidirectional/pull
synchronization, and deletion propagation for hard-deleted workout sets
(TD-004). See the roadmap and technical debt register.

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
./gradlew assembleDebug              # build the debug APK
./gradlew testDebugUnitTest          # unit tests (JVM/Robolectric)
./gradlew connectedDebugAndroidTest  # instrumented tests — needs a running emulator/device
```

#### Configuring the backend URL

The app reads its base URL from `BuildConfig.API_BASE_URL`, populated at build
time from `android/local.properties` (machine-specific and gitignored, so your
address is never committed).

**Emulator: no configuration needed.** The default is `http://10.0.2.2:8080/api/v1/`
— `10.0.2.2` is the host machine's loopback as seen from the emulator.

**Physical device:** point it at your machine's LAN address, with both on the
same Wi-Fi:

```properties
# android/local.properties
apiBaseUrl=http://192.168.1.7:8080/api/v1/
```

Then rebuild and reinstall (`./gradlew installDebug`) — the value is baked in at
build time. A trailing slash is added automatically if you omit it, since
Retrofit rejects a base URL without one.

Notes:
- Debug builds permit cleartext HTTP to any host (`src/debug/res/xml/network_security_config.xml`);
  release builds keep the platform default of cleartext forbidden. Android has
  blocked cleartext by default since API 28, so without this every request fails
  with `UnknownServiceException`.
- The app is fully usable with no backend at all — writes stay `PENDING` locally
  and upload whenever one becomes reachable.

## Key architectural decisions

- **Offline-first:** the UI observes Room via Flow and never calls the network
  directly (ADR-0002); the backend is the permanent source of truth (ADR-0003).
- **UUIDv4 generated on-device**, UTC millisecond timestamps, `BigDecimal` for
  weight/RPE/RIR to stay exact with the backend.
- **Immutable, snapshot-based workout history:** starting a workout copies the
  routine's exercises so later routine edits never change past workouts.
- **Feature-first packages, single Gradle module, MVVM + Repository**, focused
  use cases only where justified (e.g. `StartWorkoutUseCase`).
