# MyFitnessLog

An **offline-first** workout tracking application. The backend is the permanent
source of truth; the Android app keeps a synchronized local copy so it works
fully offline.

> **Version 1.9.0 released, 13 August 2026.**
> [Latest release](https://github.com/jeyaprakash1910/MyFitnessLog/releases/tag/v1.9.0) ·
> [V1 release notes](docs/V1_RELEASE_NOTES.md) ·
> [CHANGELOG](CHANGELOG.md)
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
backend/   Spring Boot REST API (Java 21, Maven, PostgreSQL, Flyway)   - M1-M2, M8, M14-M15 done
android/   Android app (Kotlin, Compose, Room, Hilt, Retrofit, WorkManager) - M3-M7, M9, M9.5, M13-M15 done
web/       React read-only history client (TypeScript, Vite, Tailwind)  - M10 done
docs/      Product, architecture, database, API, sync, coding standards, ADRs
```

## What works today

- **Backend:** the full REST contract over a Flyway-managed PostgreSQL schema —
  read-only exercise/category APIs plus write/read APIs for routines (CRUD +
  duplicate), routine exercises (add/update/remove/reorder), and workout sessions
  (history/detail/start/complete/discard) with their exercises and sets. Idempotent
  UUID-keyed upserts (201 create / 200 replay), valid status transitions (409 on
  illegal), and a nested workout-detail snapshot. A completed workout accepts
  set-level corrections but keeps its planning snapshot locked; a discarded one
  accepts nothing (ADR-0018). Client mistakes return the right status rather than
  500 (404/405/415/400). The single V1 user is attached server-side. Interactive
  docs at `/swagger-ui/index.html`.
- **Android (offline-first, Room is the source of truth):**
  - Exercise library: its own tab — download + local cache, list, local search,
    category filter. Downloaded on demand (categories then exercises, in that
    order — exercises reference categories by foreign key).
  - Routine management: list, detail, create/edit (add/remove/reorder exercises,
    edit targets), duplicate, soft-delete.
  - Workout logging: start/resume a workout from a routine (immutable snapshot of
    the routine) **or a manual/ad-hoc workout** (no routine); log sets,
    complete/discard, workout + rest timers. Once completed, a workout can only
    be changed through the narrow corrections below, never from this screen.
  - Workout history: a list of completed workouts led by the routine's name - a
    Push day reads "Push", not "Routine Workout" - with the date, derived duration
    (in seconds under a minute, so a short session never reads "0m"), exercise
    count and notes preview, plus a detail screen (metadata + every snapshotted
    exercise and set), read through a dedicated read-only repository over the
    snapshot tables. The name is recorded when the workout starts, so renaming a
    routine never relabels the workouts already done with it.
  - Correcting a finished workout: tap any set on the detail screen to fix its
    weight, reps or RPE, add a set that was performed but never logged, or delete
    one that was logged but not performed (behind a confirmation, since it is the
    only correction that removes a record). Deliberately narrow, so a wrong number
    can be fixed while the record still means something: the planning snapshot
    (exercise name, order, targets) stays locked, and a discarded workout cannot be
    edited at all (ADR-0018).
  - Discarding a workout that should not be in the record at all: open it from
    History and choose "Discard Workout", behind a confirmation that says what it
    costs. It leaves history here, on the web and on every other device, and stops
    counting towards what previous workouts suggest. Nothing is destroyed - the
    sets survive and are reachable through the API - but the app offers no way
    back.
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
  - Restore and refresh: a device with no data of its own rebuilds from the
    backend at launch, and thereafter reconciles after every sync pass, so a
    change made anywhere reaches it. The backend wins for any row with no pending
    local change; anything the outbox still owns is left alone (ADR-0017).
- **Web history viewer** (read-only): the synchronized history list and full
  workout detail — every snapshotted exercise and set with weight, reps,
  category and RPE/RIR. Formatting is a verified mirror of the Android app's,
  and decimal precision is preserved from PostgreSQL `NUMERIC` to rendered text
  rather than being lost to JavaScript floats. Responsive from mobile to
  desktop with zero axe WCAG 2.1 A/AA violations.
- **853 automated tests**, every one of them run on every push by CI: **559
  Android** (551 JVM/Robolectric + 8 instrumented on an emulator, verified
  identical on physical hardware), **161 backend** (JUnit 5/MockMvc over real
  PostgreSQL, incl. an end-to-end sync-graph idempotency proof), and **133 web**
  (Vitest + React Testing Library). Nine of them drive the real stack against a
  running backend; they skip unless one is named explicitly, and refuse to run
  against a backend that does not declare itself disposable, so a test can never
  write to real data (TD-013). Counting them, the suite is 853 tests; by default
  844 run and those nine skip.

  The instrumented tests only began running automatically on 2026-08-07. Seven of
  the eight are Room migration tests, and until then nothing executed them, which
  is how a migration with a wrong column type reached main and had to be caught by
  hand.

  The suite is also no longer flaky. It used to fail about one run in four with
  nothing wrong in the code under test; both families of flake were found and
  fixed at the cause in 1.7.0 and 1.8.0 (TD-015, TD-017), the first verified over
  100 consecutive full-suite runs.

Not yet built: authentication and multi-user support (V2), and history pagination
(TD-010). See the roadmap and technical debt register.

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
mvn test                     # 161 tests (JUnit 5 + MockMvc) against the test database
```

If you use VS Code with the Red Hat Java extension, keep
`"java.autobuild.enabled": false` (already set in `.vscode/settings.json`). With
autobuild on, the language server recompiles MapStruct's generated mappers into
Maven's `target/classes` after every build and breaks the suite. That was TD-016,
resolved 2026-08-07.

Tests run against a real PostgreSQL (faithful to the quoted-identifier schema and
CHECK constraints), configured through the `TEST_DB_*` env overrides in
`src/test/resources/application.yml`. CI uses the same overrides against a
Postgres 17 service container, matching the Supabase major version in production.

**If your PostgreSQL is not on 5432**, export the connection first, or `mvn test`
fails with `Connection to localhost:5432 refused`. On the current development
machine Homebrew's `postgresql@17` listens on **5433**, because the server on 5432
is an orphaned 16.x whose binaries an upgrade removed:

```bash
export TEST_DB_URL=jdbc:postgresql://localhost:5433/myfitnesslog_test
export TEST_DB_USERNAME=myfitnesslog
export TEST_DB_PASSWORD=myfitnesslog
```

The default stays 5432 because that is correct for CI and for a standard install.

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

**Debug and release are resolved separately, and debug never inherits from
release.** That separation is the point: `apiBaseUrl` has to name production for
`assembleRelease` to work, and while a single property served both, every debug
build wrote into the real training record (TD-018).

| Build | Property | Default |
|---|---|---|
| Debug | `debugApiBaseUrl` | `http://localhost:8080/api/v1/` |
| Release | `apiBaseUrl` | none for real use — must be set |

If the two resolve to the same URL the **build fails**, rather than warning. A
debug build can be pointed at production, but only by naming it deliberately.
Likewise `debugApiKey` is separate from `apiKey`, and empty by default, so the
production key is not compiled into debug builds.

**Development, on both an emulator and a physical device:**

```bash
# 0. once: a local database for the backend to migrate into
brew services start postgresql@17
createdb -O myfitnesslog myfitnesslog

# 1. a local backend (default profile, local Postgres, no API key)
#    JAVA_HOME is commonly pinned to 17 for Android's Gradle toolchain, and the
#    backend needs 21 — otherwise it dies with UnsupportedClassVersionError
#    (class file version 65.0 vs 61.0), which reads like a corrupt build.
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run

# 2. make "localhost" mean this machine, on the device or emulator
adb reverse tcp:8080 tcp:8080

# 3. build and install
cd android && ./gradlew installDebug
```

`adb reverse` is why the default is `localhost` rather than the emulator's
`10.0.2.2` host loopback: `10.0.2.2` only ever works on an emulator, while
`localhost` plus `adb reverse` works identically on both, with no address to
configure and no way to reach the internet by accident. Re-run `adb reverse` after
reconnecting a device or restarting the emulator — it does not survive either.

To use a LAN address instead (device and machine on the same Wi-Fi, no USB), set
`debugApiBaseUrl=http://192.168.1.42:8080/api/v1/`. Rebuild after any change — the
value is baked in at build time. A trailing slash is added automatically if you
omit it, since Retrofit rejects a base URL without one.

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
