# Changelog

All notable changes to MyFitnessLog are recorded here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.1] - 2026-08-10

### Fixed

- **Checking for updates no longer fails while the server is waking up.** The
  backend runs on a free instance that sleeps after inactivity, and the first
  request afterwards waits for it to boot: 22 seconds measured, and 100.8 seconds
  in one deploy log. The app allowed 10 seconds for every request, which is
  OkHttp's default and was never changed, so that first request could not succeed.
  It surfaced on the update check because that runs once on launch with no retry;
  background sync had been hiding the same fault for months, since WorkManager
  retries and the second attempt finds a warm server.

  The client now allows 120 seconds to read and 15 to connect. Connect stays short
  so a genuinely dead network still fails quickly. There is deliberately no overall
  call timeout, because an in-app update streams an 8.7 MB APK through the same
  client.

  A timeout now reports "the server is waking up, try again in a moment" rather
  than "could not reach the server", since trying again is the entire remedy.

## [1.6.0] - 2026-08-10

Editable workout history, plus the test and delivery infrastructure built
alongside it. Two contract changes for anything calling the API: the error
statuses below, and corrections to a completed workout.

### Added

- **A completed workout can be corrected** (ADR-0018, ADR-0017 Stage 3). The
  backend now accepts set-level writes on a `COMPLETED` session: fix a mistyped
  weight or rep count, add a set that was performed but never logged, delete one
  logged by mistake. Until now a wrong number was permanent, which was a poor
  answer for the one thing this application exists to record faithfully.

  Deliberately narrower than "edit anything". The **planning snapshot stays
  locked**: exercise name, order, target sets, target rep range and target rest
  cannot change once a session is no longer in progress, and exercises cannot be
  added or removed. Those fields record what the plan *was on the day*, and
  rewriting them is the exact failure ADR-0004 exists to prevent. Correcting what
  you performed is not the same act as rewriting what you intended.

  A **discarded** workout stays immutable, because discarding is a deletion rather
  than a record. Conflicts are last-write-wins on the server's `updatedAt`, needing
  no new machinery: Stage 2's refresh already resolves the download direction and
  uploads are idempotent upserts.

- **Correct a set from the workout history screen.** Tap any set on a finished
  workout to fix its weight, reps or RPE. The exercise name appears as a heading in
  that dialog rather than as a field, which is how the screen shows what is not
  editable instead of failing with an error afterwards. Clearing the RPE field
  removes an effort score entered by mistake.

  The correction is queued like any other change, so it uploads in the background
  and cannot be overwritten by the server before it does.

- **Continuous integration.** Four jobs on every push and pull request: Android
  unit tests with lint and a release-variant compile, Android instrumented tests
  on an emulator, backend tests against a PostgreSQL 17 service container, and
  the web suite with typecheck, lint and format. All 796 tests now run
  automatically; previously none did.
- **The instrumented tests run for the first time.** Six of the seven cover Room
  migrations. They live in `androidTest/`, need a device, and until now nothing
  could provide one, so nothing ever executed them.
- Coverage for the `X-API-Key` boundary, the only thing between a public backend
  URL and personal training history, which had none.
- Coverage for the exercise endpoints, which had none. This is the catalogue a
  fresh install downloads when rebuilding itself from the backend.
- **Flake containment on CI.** A failed Android test is retried once and
  reported as **FLAKY** rather than green, so TD-015 costs a retry inside the job
  instead of a blocked pipeline while staying visible and countable. Capped, so a
  broadly broken suite still fails outright. Local runs are untouched and show
  the truth.

### Changed

- **Client mistakes no longer return 500** (TD-001, open since Milestone 1).
  Exceptions Spring raises before a controller is reached were all falling
  through to the catch-all handler:

  | Request | Was | Now |
  |---|---|---|
  | Path with no endpoint | 500 | 404 |
  | Unsupported method | 500 | 405, with an `Allow` header |
  | Malformed UUID or query value | 500 | 400 |
  | Unsupported media type | 500 | 415 |
  | Missing required parameter | 500 | 400 |

  A 500 now means the server genuinely failed. `docs/API_SPECIFICATION.md` is
  updated to match.

### Fixed

- **A tap on the RPE picker could silently do nothing.** `WorkoutViewModel` wrote
  a row's weight and reps into one field and then read them back out of `uiState`,
  which is a projection recomputed asynchronously. When it had not caught up the
  set was not recorded and no rest timer started, with no error shown. Frames
  normally elapse between typing values and picking an RPE, so this was rare in
  the app; it is what made the test suite flaky (TD-015). The values are now read
  from the field that owns them.
- **The backend test suite runs in a working copy again** (TD-016). VS Code's
  Java language server was recompiling MapStruct's generated mappers into
  Maven's `target/classes` about a second after every build, and Eclipse's
  compiler emits class files even when references do not resolve, so a mapper
  lost the clause declaring which interface it implements. Spring registered the
  bean but could not match it to the interface, and roughly 77 tests failed. The
  fix is `"java.autobuild.enabled": false`, now committed in
  `.vscode/settings.json` so it ships with the repository.
- Two locale defects Android lint had been reporting into a void: a date
  formatter that captured the locale once at class-load and kept using it after
  the user changed language, and a download size formatted with whatever decimal
  separator the JVM defaulted to.

### Documentation

- TD-015 records both fixed causes, the measurements over 40 runs, and **five**
  approaches now ruled out with data, two of which made the flake dramatically
  worse. The flake rate is down from about 1 full-suite run in 4 to 1 in 40.
- Corrected four passages stating the repository has no CI, three of which
  described as unenforced the migration guard that now runs on every push.
- `docs/development/TESTING.md` gains a continuous-integration section.
- TD-015 records a measured flake rate and both of its signatures, so a future
  fix has a baseline to beat rather than an impression.

## [1.5.0] - 2026-08-07

Your phone now stays in step with the server on its own. The second stage of
ADR-0017: synchronisation was upload-only, and this adds the read back.

### Added

- **Periodic refresh from the backend.** Changes made anywhere appear on the
  device within about 15 minutes, unprompted.

### Fixed

- Removing an exercise during a workout now reaches the backend, so it stays
  removed (TD-014). Previously the removal was local only and a later refresh
  could bring it back.

### Notes

- The refresh never overwrites a local change that has not yet uploaded, and
  never touches a workout in progress. Verified on hardware: a routine renamed
  directly on the server appeared on the device unprompted, while an in-progress
  workout was left untouched by the same pass.

## [1.4.0] - 2026-08-07

Your history now survives losing your phone. The first stage of ADR-0017, and
the answer to the gap called out in the 1.0.0 notes: the backend was the durable
copy but could not repopulate a device.

### Added

- **Restore on a fresh install.** A device with no data of its own rebuilds from
  the backend at first launch: routines with their exercises, and completed
  workouts. Nothing to press, and it only runs when the local database is empty.
  If the backend cannot be reached it stays quiet and tries again next launch.
- A read endpoint returning a routine together with its exercises, which no
  endpoint previously provided.

### Fixed

- Android Auto Backup was restoring a stale database ahead of the app's own
  restore, which then saw a non-empty database and declined to run. The Room
  database is now excluded from both cloud backup and device transfer.

### Notes

- Verified by fully uninstalling and reinstalling on hardware: everything came
  back.

## [1.3.0] - 2026-08-06

No functional change. This release exists so the in-app updater could be
exercised against a real published release on real hardware, which it was.

## [1.2.0] - 2026-08-06

Your phone now tells you when there is a new version. Distribution is store-less,
so before this nothing told an installed build that a newer one existed
(ADR-0016).

### Added

- **In-app updates.** An update banner on the home screen when a newer release
  exists, suppressed during a workout. The update screen shows the release notes
  and download progress, then hands the APK to the system installer. Settings >
  About shows the installed version and checks on demand.
- Backend endpoints that resolve the latest GitHub release and stream its APK,
  authenticated server-side so the repository can stay private.
- Editable per-exercise notes during a workout.

### Changed

- A dark green and teal theme across all screens, including the active-workout
  table and elevated card surfaces.

### Fixed

- Completing a set with the checkbox no longer discards an RPE already entered
  for that set.

## [1.1.0] — 2026-07-30

A redesign of the active workout-logging experience alongside the operational work
that makes the system deployable to the cloud: an app-level authentication boundary,
a snake_case database baseline, and production datasource, backup, and deployment
configuration. The external REST/DTO and sync contract is unchanged.

### Added

- Inline set logging: edit weight and reps directly in the set table, with previous
  performance shown per set and one-tap completion.
- RPE capture through a bottom sheet that records effort and completes the set in one
  action.
- A per-exercise rest duration, editable during the workout, feeding a single rest
  countdown that continues while navigating between screens.
- Session-local exercise management: reorder and remove exercises for today's workout
  without changing the underlying routine.
- A persistent workout indicator on other screens showing the routine, elapsed time,
  and current exercise, with resume and a confirmed discard.
- An app settings screen backed by DataStore.
- An application launcher icon across all mipmap densities.
- **API-key authentication.** The backend enforces an `X-API-Key` boundary
  (default-deny, health endpoint open); the Android client attaches the key on every
  request. App-level, not per-user — the seam for later JWT/multi-user auth (ADR-0013).
- **Production deployment readiness.** A `prod` Spring profile with a Supabase
  dual-pooler datasource (transaction pooler at runtime, session pooler for Flyway) and
  free-tier connection tuning; a daily `pg_dump` backup workflow with a tested restore;
  and deployment/secrets/backup/cutover documentation.
- Project metadata and GitHub community health files (LICENSE, CONTRIBUTING,
  CODE_OF_CONDUCT, SECURITY) and a documentation index.

### Changed

- Set logging is now event-driven: a set becomes part of history only when it is
  completed, so planned and partially-entered rows never enter history, sync, or
  analytics. Editing a completed set changes its values without re-triggering
  completion or rest.
- Undoing a completed set restores it in place as an editable row rather than leaving a
  record behind.
- **Database identifiers are now unquoted `snake_case`** (ADR-0012). The greenfield
  Flyway baseline and JPA mappings were rewritten before any production data exists;
  the REST/DTO contract and the Android client are unaffected.

### Documentation

- Added a public [workout-logging architecture reference](docs/architecture/WORKOUT_LOGGING.md)
  and architecture decision records
  [ADR-0008](docs/ADR/0008-event-driven-set-logging.md) through
  [ADR-0013](docs/ADR/0013-api-key-auth-boundary.md).

## [1.0.0] — 2026-07-22

First release: a signed Android app and a read-only web viewer, both talking to a
backend on a private network. See the full
[release notes](docs/V1_RELEASE_NOTES.md).

### Added

- **Routines.** Create, rename, duplicate, and delete routines; add exercises with
  target sets, a rep range, and rest time; reorder and remove them.
- **Workouts.** Start from scratch or from a routine, add exercises mid-session, log
  sets with weight, reps, RPE/RIR, and a set category, run a rest timer, then complete
  or discard. Elapsed time is derived from the start timestamp, so it survives process
  death.
- **Exercise library.** Exercises across many categories, downloaded from the backend
  and cached locally, with search and category filtering.
- **History.** Every completed workout is an immutable snapshot of the exercises and
  targets as they were on the day; later routine or catalogue edits never rewrite it.
- **Synchronization.** One-way, Android → backend, in the background. Everything works
  offline; writes queue locally and upload when a backend is reachable, with idempotent
  UUID-keyed replay.
- **Web viewer.** Read-only history list and full workout detail, responsive from
  mobile to desktop, with decimal weights preserved exactly from the database.

### Notes

- Version 1 is a local production release for personal or trusted-group use on a
  private backend. There is no authentication — the network boundary is the security
  boundary. Multi-user support is a future version.
- Synchronization is one-way: the backend is the durable copy but cannot repopulate a
  device, so a phone that loses its database does not get its history back.

[Unreleased]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.6.1...HEAD
[1.6.1]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/jeyaprakash1910/MyFitnessLog/releases/tag/v1.0.0
