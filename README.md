# MyFitnessLog

An **offline-first** workout tracking application. The backend is the permanent
source of truth; the Android app keeps a synchronized local copy so it works
fully offline.

> **Version 1.3.0 released, 6 August 2026.**
> [Latest release](https://github.com/jeyaprakash1910/MyFitnessLog/releases/tag/v1.3.0) ·
> [V1 release notes](docs/V1_RELEASE_NOTES.md)
>
> Build routines, log workouts fully offline, and have them synchronize to the
> backend in the background. The signed release build is verified on physical
> hardware (OnePlus CPH2717, Android 16) against a live backend and PostgreSQL,
> with the resulting rows confirmed in the database. The backend implements the
> full write/read REST contract with idempotent sync semantics, and the read-only
> web client renders synchronized history and workout detail in the browser.
>
> Version 1 is a **local production release**: a signed APK against a backend on
> your own network. As of 1.1.0 the backend enforces an app-level `X-API-Key`
> authentication boundary (ADR-0013) so it can be exposed to the public internet,
> and the client sends the key on every request; the backend still attaches a
> single default user, so this is app-level, not per-user, auth. Per-user
> (multi-user) authentication remains a future version.
>
> No prebuilt APK is distributed: each install needs its own backend URL compiled
> in, so build the first one yourself (see
> [Building a release](#building-a-release)). Since 1.2.0 every update after that
> installs over the air from the app itself (see
> [In-app updates](#in-app-updates)), verified on physical hardware.
> See [docs/ROADMAP.md](docs/ROADMAP.md) for authoritative status and
> [docs/TECH_DEBT.md](docs/TECH_DEBT.md) for known limitations.

## Repository layout

```
backend/   Spring Boot REST API (Java 21, Maven, PostgreSQL, Flyway)   — M1–M2, M8 done
android/   Android app (Kotlin, Compose, Room, Hilt, Retrofit, WorkManager) — M3–M7, M9, M9.5 done
web/       React read-only history client (TypeScript, Vite, Tailwind)  — M10 done
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
  - In-app updates: on launch the app asks the backend for the latest published
    release and, when a newer one exists, shows a dismissible banner (suppressed
    during a workout). The update screen shows the release notes and download
    progress, then hands the APK to the system installer. Settings > About shows
    the installed version and checks on demand. Distribution is store-less, so
    this is the only thing that tells an installed build a new one exists
    (ADR-0016).
  - Background synchronization: every local write is uploaded to the backend in
    dependency order by a WorkManager-scheduled pass — offline-first throughout
    (the UI never waits on the network), with idempotent replay, exponential
    backoff, failure isolation per aggregate, and recovery of work stranded by
    process death.
- **Web history viewer** (read-only): the synchronized history list and full
  workout detail — every snapshotted exercise and set with weight, reps,
  category and RPE/RIR. Formatting is a verified mirror of the Android app's,
  and decimal precision is preserved from PostgreSQL `NUMERIC` to rendered text
  rather than being lost to JavaScript floats. Responsive from mobile to
  desktop with zero axe WCAG 2.1 A/AA violations.
- **699 automated tests**: **467 Android** (461 JVM/Robolectric + 6
  instrumented, verified identical on emulator and physical hardware),
  **99 backend** (JUnit 5/MockMvc over real PostgreSQL, incl. an end-to-end
  sync-graph idempotency proof), and **133 web** (Vitest + React Testing
  Library). Nine of them drive the real stack against a running backend; they
  skip unless one is named explicitly, and refuse to run against a backend that
  does not declare itself disposable, so a test can never write to real data
  (TD-013). Counting them, the suite is 699 tests; by default 690 run and those
  nine skip.

Not yet built: authentication and multi-user support (V2), bidirectional/pull
synchronization, and history pagination (TD-010). See the roadmap and technical
debt register.

Because synchronization is upload-only, a device shows only the workouts logged
**on that device**. History created on another install is uploaded to the backend
and visible in the web client, but never pulled down. This surfaces as apparently
missing history when more than one install is in use, and is the pull-sync gap
above rather than a fault.

## Documentation (read these first)

For the full, categorized index see **[docs/README.md](docs/README.md)**. The essentials:

| Doc | Purpose |
| --- | --- |
| [docs/ROADMAP.md](docs/ROADMAP.md) | **Start here** — current status, milestones, phase-by-phase progress |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System-wide architecture (all three apps) |
| [docs/ANDROID_ARCHITECTURE.md](docs/ANDROID_ARCHITECTURE.md) | Android architecture, conventions, and the *why* behind decisions |
| [docs/architecture/WORKOUT_LOGGING.md](docs/architecture/WORKOUT_LOGGING.md) | Workout-logging architecture — session lifecycle, set state machine, rest/previous/ordering/indicator, invariants |
| [docs/ANDROID_FLOW.md](docs/ANDROID_FLOW.md) | Screen-by-screen app flow + implementation status |
| [docs/DATABASE.md](docs/DATABASE.md) | Canonical relational data model |
| [docs/API_SPECIFICATION.md](docs/API_SPECIFICATION.md) | REST API contract (frozen) |
| [docs/SYNC.md](docs/SYNC.md) | Synchronization strategy and the implemented upload contract |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | Coding conventions |
| [docs/development/TESTING.md](docs/development/TESTING.md) | Testing guide — layers, how to run each suite, disposable-backend and device-guard rules |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute — philosophy, workflow, review, testing & docs expectations |
| [CHANGELOG.md](CHANGELOG.md) | Notable changes per version (Keep a Changelog) |
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
mvn spring-boot:run          # Flyway migrates on start; API at :8080/api/v1
./scripts/run-tests.sh       # 110 tests (JUnit 5 + MockMvc) against the test database
```

**Use the script, not `mvn test` directly.** The suite fails in the working copy
and passes everywhere else for reasons that are not understood; the script runs it
at HEAD in a disposable worktree, which is a configuration known to pass. It tests
committed content, so it warns when you have uncommitted changes. See TD-016.
Tests run against a real PostgreSQL (faithful to the quoted-identifier schema and
CHECK constraints), configured through the `TEST_DB_*` env overrides in
`src/test/resources/application.yml`. CI uses the same overrides against a
Postgres 17 service container, matching the Supabase major version in production.

All three suites run automatically on every push and pull request
(`.github/workflows/ci.yml`).

### Android
Requires JDK 17 and the Android SDK (`ANDROID_HOME` / `local.properties`).
```bash
cd android
./gradlew assembleDebug              # build the debug APK
./gradlew testDebugUnitTest          # unit tests (JVM/Robolectric)
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest  # instrumented — emulator only
# Never point connectedAndroidTest at a daily-use phone: it uninstalls the app,
# which deletes its database. The build blocks it. See CODING_STANDARDS 20c.
```

#### Building a release

```bash
cd android
./gradlew assembleRelease            # signed APK, if credentials are configured
adb install -r app/build/outputs/apk/release/app-release.apk
```

Signing credentials come from `android/local.properties` (gitignored) or `MFL_*`
environment variables. Without them the build still succeeds but produces an
**unsigned** APK — it warns when it does, and an unsigned APK installs nowhere.

The version lives in `android/version.properties`. Edit `versionName` only;
`versionCode` is derived from it, so the two cannot disagree.

**[docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) is the authoritative
procedure** — including backing up the keystore (lose it and no installed copy
can ever be updated) and backing up PostgreSQL before installing anything.

#### In-app updates

The first install is by hand, as above. After that an installed build finds its
own updates: it asks the backend for the latest release on launch, shows a banner
when there is a newer one, and downloads and installs it on the phone. No cable.

Releasing therefore means **attaching the signed APK to the GitHub release** and
tagging `vMAJOR.MINOR.PATCH`. The backend resolves that release with a read-only
GitHub token and streams the APK to the app; the token stays server-side, because
the repository is private and a secret inside a distributed APK is a published
secret. Enabled by setting `APP_UPDATE_REPOSITORY` and `APP_UPDATE_GITHUB_TOKEN`
on the backend. Unset, the endpoints answer 503 and no update is ever offered.

Design and alternatives considered:
[ADR-0016](docs/ADR/0016-in-app-update-delivery.md).

### Web

Requires Node 20+.

```bash
cd web
cp .env.example .env.local     # sets VITE_API_BASE_URL; the app refuses to start without it
npm install
npm run dev                    # http://localhost:5173
npm run test                   # 133 tests (Vitest + React Testing Library)
npm run build                  # type-check and production build
```

The backend must be running for the app to show anything: the web client is
read-only and renders synchronized history straight from the API. `VITE_API_BASE_URL`
has no default on purpose — a silently-wrong base URL is harder to diagnose than
a refusal to start.

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
apiBaseUrl=http://192.168.1.42:8080/api/v1/   # your machine's LAN address
```

Then rebuild and reinstall (`./gradlew installDebug`) — the value is baked in at
build time. A trailing slash is added automatically if you omit it, since
Retrofit rejects a base URL without one.

Notes:
- Android has blocked cleartext HTTP by default since API 28, so without an
  explicit policy every request fails with `UnknownServiceException`.
  **Debug** builds permit cleartext to any host
  (`src/debug/res/xml/network_security_config.xml`) — acceptable only because
  debug builds are never distributed. **Release** builds permit it to exactly
  the one host `apiBaseUrl` names and deny it everywhere else; that config is
  generated at build time, so it cannot drift from the URL the app actually
  uses. Point `apiBaseUrl` at an `https://` URL and the exemption disappears
  entirely.
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
