# Testing Guide

This guide describes how MyFitnessLog is tested: the philosophy, the layers, how to
run each suite, and the safety rules that keep tests away from real data. It
complements [CODING_STANDARDS.md](../CODING_STANDARDS.md) (the authoritative
conventions) and the build instructions in the [README](../../README.md); where those
already cover something, this points at it rather than repeating it.

## Philosophy

- **Verify by running.** A change is not done when it compiles — it is done when it
  builds *and its tests pass*. Runtime verification after every unit is a core working
  rule, not an afterthought.
- **Test behaviour at the layer that owns it.** Domain rules are tested as pure unit
  tests; persistence is tested against a real database; UI behaviour is tested with
  Compose. A test lives where the behaviour it protects lives.
- **A rule worth stating is worth a failing test.** For a critical invariant, prefer a
  test that fails when the rule is broken (a mutation-style check) over one that merely
  exercises the happy path.
- **Tests never touch real data.** This is absolute — see
  [Disposable-backend rule](#disposable-backend-rule).

## Testing layers

| Layer | What it covers | Tooling |
|---|---|---|
| **Domain (pure)** | Business rules with no Android/DB/coroutine dependencies — e.g. the set logging state machine | JUnit (plain JVM) |
| **Repository / persistence** | DAO queries and repository behaviour against a real schema | Robolectric + in-memory/real database |
| **UI (Compose)** | Screen behaviour and state rendering | Compose test + Robolectric |
| **Backend service/web** | Service logic, controllers, and REST contract; web rendering | JUnit 5 + MockMvc; Vitest + React Testing Library |
| **Instrumented (Android)** | Database migrations and end-to-end flows that need a real device | Android instrumentation on an emulator |

Pure domain logic is the cheapest and most valuable place to test — the workout-logging
state machine, for instance, is exhaustively unit-tested with no Android dependencies
(see [WORKOUT_LOGGING.md](../architecture/WORKOUT_LOGGING.md)). Push logic down into
that layer so it can be tested there.

## Running the tests

Full build and run instructions per component are in the [README](../../README.md).
The common commands:

**Backend** (JDK 21; a local PostgreSQL test database):
```bash
cd backend
mvn test
```
Backend tests run against a real PostgreSQL, faithful to the quoted-identifier schema
and CHECK constraints. If yours is not on the default port 5432, export
`TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD` first, or the run fails with
`Connection to localhost:5432 refused` (the README's backend section has the exact
values for this machine, where PostgreSQL 17 is on 5433).

If you use VS Code with the Red Hat Java extension, leave
`"java.autobuild.enabled": false` set in `.vscode/settings.json`. With autobuild on,
the language server recompiles MapStruct's generated mappers into Maven's
`target/classes` a second after each build and roughly 77 tests fail with a missing
mapper bean. That was TD-016.

**Android** (JDK 17; Android SDK):
```bash
cd android
./gradlew testDebugUnitTest      # JVM/Robolectric unit tests
./gradlew lintDebug              # Android lint; CI fails on errors
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest   # instrumented
```
The instrumented tests are emulator-only by construction; see
[Physical-device guard](#physical-device-guard-android-instrumented-tests).

**Web** (Node 20+):
```bash
cd web
npm run test                     # Vitest + React Testing Library
npm run typecheck && npm run lint && npm run format:check
```

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and every pull request. Four
jobs, in parallel:

| Job | What it runs |
|---|---|
| Android unit tests | `testDebugUnitTest`, release-variant compile, `lintDebug` |
| Android instrumented tests | `connectedDebugAndroidTest` on an API 30 emulator |
| Backend tests | `mvn clean test` against a PostgreSQL 17 service container |
| Web | typecheck, lint, format check, Vitest |

The instrumented job exists because of a specific gap. Six of the seven instrumented
tests cover Room migrations, and until 2026-08-07 nothing ran them: they live in
`androidTest/`, which needs a device, and no job could provide one. In that window a
migration with a wrong column type reached `main` and was caught by hand. Migration
defects are the worst thing this project can ship, because synchronisation is one-way
(ADR-0003) and a corrupted local database cannot be rebuilt from the backend.

A failed Android unit test is retried once and reported as **FLAKY** rather than
green. TD-015 was resolved on 2026-08-10, so this no longer contains a known flake;
it is kept so a future one is counted here rather than discovered by someone
re-running a red build by hand. Anything red is a real failure.

## Integration and live-stack tests

A small number of tests drive the real stack against a running backend (for example,
the sync-graph idempotency checks). They do **not** run by default: each is gated on an
explicitly-named target and skips when it is unset. They exist to prove the wire
contract end-to-end, not to run on every build.

## Disposable-backend rule

**No automated test may read or write the production database.** Not "by default", not
"unless a backend happens to be running" — never. This is spelled out in
[CODING_STANDARDS §19b](../CODING_STANDARDS.md); the essentials:

A test that talks to a real server must satisfy **both** conditions:

1. **Its target is named explicitly, with no default.** An unset environment variable
   (`MFL_LIVE_TEST_BASE_URL` on Android, `VITE_LIVE_TEST_BASE_URL` on web) means the
   test skips.
2. **The server declares itself disposable** via `disposable: true` from
   `GET /api/v1/health` — which only the backend's dedicated disposable profile does.
   If a server answers without declaring it, the test **fails** rather than skips, so a
   misconfiguration surfaces instead of silently writing into real data.

Disposability is never inferred. Reachability, port, and hostname describe *where* a
server is, not *what it holds* — only the server can state that it is safe to write to.
This rule exists because it was learned expensively: a guard that assumed "no backend
is running" silently stopped protecting the data once a backend was always running, and
test rows leaked into real history ([TECH_DEBT TD-013](../TECH_DEBT.md)). The general
lesson: a safeguard that encodes an assumption about the environment stops protecting
you the moment the environment changes — check the property you actually care about.

## Physical-device guard (Android instrumented tests)

Instrumented Android tests run on an **emulator**, never a daily-use phone. The
`connectedAndroidTest` task installs and then *uninstalls* the app, and an Android
uninstall deletes the app's database — which, because synchronization is one-way
([SYNC.md](../SYNC.md)), the backend cannot restore. The build refuses to run
device-lifecycle tasks against anything that is not an emulator.

Run them on the emulator:
```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

To exercise them on real hardware (worthwhile, and they pass there), install the APKs
manually and invoke the runner directly — this performs no uninstall — and back up the
database first. The exact commands are in
[CODING_STANDARDS §20c](../CODING_STANDARDS.md).

## Database migration tests

Because a schema change that is not migrated destroys unsynchronized data, every schema
change carries a test that proves data *survives* the migration — not merely that the
migration runs. This applies to both Flyway (backend) and Room (Android); the Room
migration test is an instrumented test and runs on the emulator. The full discipline is
in [CODING_STANDARDS §20/§20b](../CODING_STANDARDS.md).

## When to write tests

Write a test when you:

- add or change **domain logic** — cover it as a pure unit test, including a check that
  fails if the rule is violated;
- add or change a **DAO query or repository behaviour** — cover it against a real
  schema;
- add or change **UI behaviour** (not styling) — cover it with a Compose test;
- change the **database schema** — add a migration test proving data survives;
- **fix a bug** — add a test that reproduces it, so it cannot silently return.

Cosmetic-only changes (copy, spacing, colour) do not need new tests, but must not break
existing ones.

## Related documents

- [CODING_STANDARDS.md](../CODING_STANDARDS.md) — authoritative testing, migration, and
  device-guard rules.
- [TECH_DEBT.md](../TECH_DEBT.md) — known limitations, including the origin of the
  disposable-backend rule (TD-013).
- [README.md](../../README.md) — per-component build and run instructions and the
  current test counts.
- [SYNC.md](../SYNC.md) — why one-way synchronization makes local data loss
  unrecoverable.
