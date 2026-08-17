# Technical Debt Register

Project: MyFitnessLog
Version: 1.6
Last Updated: August 17, 2026 (TD-018 resolved: debug and release resolve their backend separately)

This document records known, accepted technical debt: deliberate limitations that are not defects in the current milestone but must be addressed in a later milestone. Each item states the observation, why it is currently acceptable, the recommended future implementation, the documentation that must change first, and when it is scheduled.

This register holds debt that outlives a single task. Short-lived working items live in the development TODO and are not duplicated here.

> **TD-015 is resolved.** The Android ViewModel test flake is fixed, verified over
> 100 consecutive full-suite runs against a same-day baseline that failed on runs 4
> and 5. It took nine failed attempts because everyone read the wrong stack trace:
> kotlinx's guard records a fault during a *read* and throws it at the next
> *write*, so the exception always pointed at an innocent teardown. The reader was
> in the cause all along. Worth reading TD-015 for that alone.

### Index

| ID | Item | Status | Blocks |
|---|---|---|---|
| TD-001 | Framework exceptions returned 500 (404/405/400/415) | ✅ Resolved 2026-08-07 | - |
| TD-002 | Category filtering in service, not repository | Open — note only | — |
| TD-003 | History endpoint scans the whole table | ✅ Resolved 2026-07-22 | — |
| TD-004 | WorkoutSet deletions never reach the backend | ✅ Resolved 2026-07-22 (ADR-0007) | — |
| TD-005 | No physical-device verification | ✅ Resolved 2026-07-22 | — |
| TD-006 | No release signing configuration | ✅ Resolved 2026-07-22 | — |
| TD-007 | Backend has no CORS configuration | ✅ Resolved 2026-07-22 | — |
| TD-008 | History differs between Android and backend | ✅ Resolved 2026-07-22 | — |
| TD-009 | RoutineEntity lacks description/displayOrder | Open — note only | — |
| TD-010 | Workout history is not paginated | Open — deferred | post-V1 (scaling) |
| TD-011 | Routine deletions never reached the backend | ✅ Resolved 2026-07-22 | — |
| TD-012 | Reference data keeps referenced withdrawn rows | Open — note only | — |
| TD-013 | Live sync tests write into the production database | ✅ Resolved 2026-07-22 (M12 Phase 3) | — |
| TD-014 | WorkoutExercise removal during a workout is not propagated to the backend | ✅ Resolved 2026-08-07 (ADR-0017 Stage 2) | - |
| TD-015 | ViewModel test classes were intermittently flaky | ✅ Resolved 2026-08-10 (three causes; verified over 100 runs) | - |
| TD-016 | Backend suite failed in the working copy, passed elsewhere | ✅ Resolved 2026-08-07 (VS Code Java autobuild overwrote Maven's output) | - |
| TD-017 | ViewModel tests sampled async writes instead of waiting | ✅ Resolved 2026-08-13 (`awaitWork`; found a real ADR-0018 regression) | - |
| TD-018 | The debug build points at the production backend | ✅ Resolved 2026-08-17 (debug/release resolve separately; equal URLs fail the build) | - |

---

## TD-016 - The backend test suite failed in the working copy and passed elsewhere

Status: **Resolved 2026-08-07.** Cause found and fixed. The backend suite now runs
normally with `mvn test` in `backend/`; no clone, worktree or script is required.

Milestone identified: ADR-0016 (2026-08-06)
Resolved: 2026-08-07

### What was happening

Running `mvn test` in `backend/` failed with ~77 errors, always the same way: the
Spring context could not start because a MapStruct mapper bean was missing. The
identical commit passed all 110 tests in a fresh clone, a worktree, or a `cp -R`
to another path.

The cause was **VS Code's Red Hat Java extension**. It imports `backend/` as an
Eclipse project, and in the classpath it generates,
`target/generated-sources/annotations` is declared as a source folder with no
output directory of its own:

```xml
<classpathentry kind="src" path="target/generated-sources/annotations">
<classpathentry kind="output" path="target/classes"/>
```

With no `output` attribute the entry falls back to the project default, which is
`target/classes` - the directory Maven has just written. MapStruct's generated
`*MapperImpl.java` files live in that source folder, so with autobuild on the
language server recompiled them into Maven's output roughly one second after
every build.

Eclipse's compiler emits class files even when references fail to resolve. The
overwritten `ExerciseCategoryMapperImpl` therefore lost its
`implements ExerciseCategoryMapper` clause and had its parameter types left
unqualified. Spring still registered the bean, but could not match it to the
interface the controller asked for, so every `@SpringBootTest` context failed on
a missing mapper.

### Why the earlier evidence was so confusing

Every previous observation is explained by this, including the two that looked
contradictory:

| Observation | Explanation |
|---|---|
| A fresh clone, worktree or `cp -R` passed | None of them is in the VS Code workspace, so the language server never touched them |
| The working copy reached through a symlink failed | A symlink reaches the same real directory the server is building into |
| `diff -r` found no difference in sources | It was the compiled output that differed, not the sources |
| One test class sometimes passed on its own | The corruption landed a second after the build, so a short run could beat it |

The path was never the variable. Whether an editor was watching was.

### The fix

One setting in `.vscode/settings.json`, committed with a comment explaining why:

```json
"java.autobuild.enabled": false
```

The language server stops writing class files entirely. Navigation, completion and
live error markers keep working, because those come from its own index rather than
from `target/classes`.

### How it was verified

1. Hashing the mapper class once a second after a build: previously rewritten at
   t+2s (2270 bytes becoming 2529), now stable.
2. `javap` on the compiled class: `implements com.myfitnesslog.mapper.ExerciseCategoryMapper`
   present again, types fully qualified.
3. `mvn -B clean test` in the working copy: **110 passed, 0 failures, 0 errors.**
   Repeated after unrelated cleanup, still 110.

### What this corrects in the earlier write-up

The previous version of this entry, and the header of the since-deleted
`backend/scripts/run-tests.sh`, both listed **class-file contents** as ruled out by
measurement. That was wrong, and it was the one place the answer was sitting. A
single `cmp` between a passing and a failing build would have ended the
investigation immediately. The lesson worth keeping: "ruled out" is only worth
recording when the check was actually run, and re-running a cheap check beats
trusting a previous conclusion.

`.DS_Store` files were also once suspected, cleared, then doubted again. They are
now ruled out properly, against a deterministic reproducer.

### Follow-up

`backend/scripts/run-tests.sh`, the worktree workaround, was **deleted on
2026-08-10** after three days of the direct `mvn test` path working. It was kept
that long deliberately, because this failure was intermittent enough to mislead
four investigations and a quick deletion would have been the same overconfidence
that caused them.

---

## TD-018 - The debug build points at the production backend

Status: **Resolved 2026-08-17.** Debug and release resolve their backend
separately, debug defaults to a local backend, and a build that configures them to
the same URL now fails rather than warns.

Milestone identified: 2026-08-12, during the ADR-0018 device verification
Resolved: 2026-08-17

### Observation

`android/local.properties` sets `apiBaseUrl` to the deployed Render backend, so a
debug build installed on an emulator downloads real routines and workout history
(ADR-0017 Stage 2) and uploads anything written during testing. There is no
separate development target.

Two consequences follow, and both were hit on 2026-08-12:

* A verification workout written on an emulator lands in the owner's history and
  cannot be removed, because until 2026-08-13 there was no way to remove a
  completed workout at all.
* Driving the UI blind for testing can operate on real workouts. It happened twice
  in one session; once an "Add set" dialog was opened on a real 3 August workout
  and cancelled before writing.

### Why it is currently acceptable

The backend is a test environment by the owner's decision (2026-08-13), so the data
it holds is disposable and a stray write costs nothing. That is a statement about
today, not about the design.

### Why it stops being acceptable without warning

This is the TD-013 pattern again. A safeguard - here, "it does not matter if tests
write to production" - encodes an assumption about the environment rather than
checking a property. The assumption expires silently the first time a real workout
is logged, and nothing fails when it does.

### How it was resolved

The cause was inheritance, not configuration. One `resolveApiBaseUrl()` served both
build types, and `local.properties` must name production for `assembleRelease` to
work — so debug got production by default, along with the production API key, and
no one had to make a mistake for it to happen.

`android/app/build.gradle.kts` now resolves the two independently:

* `resolveReleaseApiBaseUrl()` reads `apiBaseUrl` — production, unchanged.
* `resolveDebugApiBaseUrl()` reads `debugApiBaseUrl` and **never falls back to
  `apiBaseUrl`**. A debug build can be pointed at production, but only by naming it.
* `resolveDebugApiKey()` reads `debugApiKey`, empty by default, so the production
  key no longer travels into debug builds either.
* If the two URLs resolve equal, the build **fails** with the reason and the fix.
  Following the device-guard precedent in the same file: the rule is structural
  rather than a convention in a document.

The debug default is `http://localhost:8080/api/v1/`, deliberately not the
emulator's `10.0.2.2` host loopback. With `adb reverse tcp:8080 tcp:8080`,
`localhost` means the development machine on an emulator *and* on a physical
device, so one default covers both targets with no address to configure — and it
can never resolve to something on the internet.

Not the `livetest` profile, which this entry previously suggested. That backend is
owned by the automated live tests, which write and clear it; sharing it with manual
development would mean a test run destroying whatever was being looked at by hand.
Debug points at the ordinary dev backend (default profile, port 8080, local
`myfitnesslog` database), which is distinct from production and from livetest
alike. Its `app.api-key` is blank, which disables authentication, so no key is
needed locally.

Verified at build level: a debug build compiles
`API_BASE_URL = "http://localhost:8080/api/v1/"` and an empty `API_KEY`; the release
build still compiles the Render URL; setting `debugApiBaseUrl` to the production URL
fails the build with the message above.

Verified end to end on the emulator (2026-08-17), against a local backend started on
the default profile with a freshly migrated database:

* The first screen read **"No routines yet."** Pointed at production it would have
  listed the real ones — the single most direct evidence available.
* A routine created in the UI went `POST http://localhost:8080/api/v1/routines` →
  201, and Room recorded it `SYNCED`, so the whole outbox cycle ran against local.
* The row is in the local `myfitnesslog` database and, queried directly against the
  live production API, **is not there** — production still holds only `Push` and
  `Push 1`.
* `adb logcat` recorded **zero** requests to the production host and zero
  `X-API-Key` headers for the entire session.

Setup friction worth knowing, now in the README: `JAVA_HOME` is commonly pinned to
17 for Android's Gradle toolchain, and the backend needs 21. Starting it with 17
fails with `UnsupportedClassVersionError` (class file version 65.0 vs 61.0), which
reads like a corrupt build rather than a version mismatch.

### What this does not solve

The debug build can no longer reach production by accident, which is the whole of
TD-018. It does not give the phone a *disposable* backend when the laptop is not
running — the default requires `adb reverse` and a backend on the development
machine. If away-from-desk testing on real hardware becomes routine, a hosted dev
instance is the answer, and `debugApiBaseUrl` is already the seam for it.

---

## TD-017 - ViewModel tests sampled asynchronous writes instead of waiting for them

Status: **Resolved 2026-08-13.** One cause behind every flake seen after TD-015,
in four test classes. Fixing it also uncovered a real production regression.

Milestone identified: 2026-08-12 (PR #24 and #26 CI failures)
Resolved: 2026-08-13

### The cause, which is the whole entry

A ViewModel action launches into `viewModelScope` and returns immediately. The
write lands later, on Room's threads. A test that reads the database on the next
line is racing it: fast machine wins, loaded CI runner loses. Green locally, red
in CI, and nothing wrong with the production code - which is exactly the profile
that makes people re-run the build instead of reading it.

Three distinct failures on 2026-08-12, all this:

| Test | Symptom |
|---|---|
| `WorkoutDetailViewModelTest` (x4) | `NullPointerException` on `awaitSuccess().correction!!` |
| `RoutineEditViewModelTest.onNameChangePersistsRename` | `ComparisonFailure` sampling the row after an async rename |
| `WorkoutViewModelTest.completedWorkoutRejectsFurtherEdits` | `AssertionError`: a set existed that should not |

### The fix

`awaitWork { vm.action() }` joins precisely the coroutines the action started,
excluding children captured beforehand so `stateIn`'s permanent collector is not
joined. It is exact rather than heuristic: when those coroutines complete, their
writes have committed, because a repository call stays suspended until Room
returns. TD-015's dedicated per-database executors are what make this reliable;
the machinery existed and nothing had used it for assertions.

The rule is now two lines in `CODING_STANDARDS` §19b: assert on exposed state with
`awaitFirst`, on side effects with `awaitWork`, and never sample.

**Negative assertions are the dangerous class.** "Nothing was written" can never be
waited for, only confirmed after the work finishes. Every unsafe site found in the
sweep was a negative assertion, and one carried the comment *"give any (incorrect)
write a chance"* - a race described rather than removed.

### What the flake was actually reporting

`completedWorkoutRejectsFurtherEdits` was not noise. ADR-0018 widened the set-write
guard to permit `COMPLETED` so that corrections could be made, and that also
stopped refusing the **logging** screen's writes to a finished session.
`WorkoutViewModel` has no read-only check of its own and its `runCatching` swallowed
the rejection it had been relying on, so from 2026-08-08 the only thing enforcing
ADR-0004 below the UI was the screen declining to render controls.

The test kept passing **because of its race**: it read the database before the
now-succeeding write landed. A deterministic test would have failed the moment the
guard was widened, which is the entire argument for this entry.

Fixed by `SetWriteIntent`. `LOGGING` (the default) permits `IN_PROGRESS` only;
`CORRECTION` also permits `COMPLETED`. The safer rule is what a caller gets by
accident, and the wider permission has to be asked for. Pinned by
`completedWorkoutRefusesALoggingWrite` and `discardedWorkoutRefusesEvenACorrection`,
and mutation-checked: weakening `LOGGING` back to ADR-0018's behaviour fails the
test deterministically, 1 run in 1, where before it failed roughly 1 in 10.

### Verification

27 consecutive full-suite runs with `--rerun-tasks` after the fix, plus the
mutation check above. One failure occurred early in that sequence whose report was
overwritten before it could be read; 27 clean runs followed and a scan for the
pattern across every test file finds no remaining unguarded site. That single
unexplained failure is recorded here rather than omitted, because "we could not
reproduce it" is how TD-015 stayed open for three days.

### The lesson worth keeping

A flaky test is a report, not an inconvenience. This one was reporting a genuine
loss of an invariant, and the reflex to re-run it would have buried that. The CI
retry exists to **count** flakes, never to hide them.

---

## TD-015 - The ViewModel test classes were intermittently flaky

Status: **Resolved 2026-08-10.** Three causes, all found and fixed. Verified over
**100 consecutive full-suite runs** against a same-day baseline that failed on runs
4 and 5.

Milestone identified: ADR-0017 Stage 1 (2026-08-07)
Resolved: 2026-08-10, after nine failed attempts

### Why it took nine attempts

Everyone, including every attempt recorded below, read the wrong stack trace.

kotlinx's guard is `NonConcurrentlyModifiable`, and its own comment says what
matters: *"The read operations never throw. Instead, the failures detected inside
them will be remembered and thrown on the next modification."* It fires only on a
**write**, meaning `setMain` or `resetMain`. So the exception's own stack always
points at whichever teardown happened to be next, and never at the code that
caused it.

The reader is attached as the **cause**. Nobody looked until 2026-08-10:

    Caused by: java.lang.Throwable: reader location
        at YieldKt.yield(Yield.kt:36)
        at CombineKt$combineInternal$2$1$1.emit(Combine.kt:30)
        at androidx.room.CoroutinesRoom$Companion$execute$4$job$1.invokeSuspend

That is the whole answer. `combine` calls `yield()` on **every emission**, `yield()`
reads the `Dispatchers.Main` delegate, and because Main is an *unconfined* test
dispatcher the continuation resumes inline **on Room's background thread**. So the
read happened on a thread the test did not control, and could overlap the test
thread's write.

Two consequences follow, and both had been reasoned about incorrectly before:

* **The failing test is not necessarily the guilty one.** A fault recorded during
  one class's read surfaces at the next class's write. An early version of this
  entry proposed exactly that and was talked out of it on the grounds that "the
  trace shows the failing class's own tearDown" - which is true, and irrelevant,
  because the trace shows the writer.
* **`uiState` is a combine over Room flows, and a directly-constructed ViewModel is
  never cleared.** Every test left one collecting for the rest of the class.
  `WorkoutViewModelTest` has 33 tests, so it performed 66 writes against a growing
  pile of readers.

### Cause 1, fixed: a real defect in WorkoutViewModel

**Not a test problem**, and it accounted for every `TimeoutCancellationException`.

`onCommitRow` wrote weight and reps into the private `drafts` flow;
`onRpeSelected` read them back out of `uiState`, a `combine`/`stateIn` projection
that had not necessarily recomputed. When it was stale, `isCompletable` was false,
the transition returned `None` with `RestEffect.NONE`, and **the tap silently did
nothing**: no set saved, no rest timer, no error.

In the app frames elapse between typing and tapping, so it almost always worked.
Fixed by `currentInput()`, which reads from the field that owns the values.
Measured in isolation: 4 of 6 runs failed with the fix reverted, 0 of 12 with it
applied. Guarded by
`WorkoutViewModelTest.rpeSelectionSeesAWeightCommittedImmediatelyBeforeIt`.

### Cause 2, fixed: teardown wrote Main while Room's threads were still reading it

Room's default executors come from `ArchTaskExecutor`. That pool is shared and
**cannot be joined**, so no teardown could ever wait for it, which is why every
attempt to make teardown safe failed.

`newInMemoryDatabase()` now gives each test database **two dedicated
single-thread executors**, and `closeAndDrain()` does three things in an order
that each earlier attempt got partly right:

1. **Cancel** the tracked ViewModels' scopes, so nothing new is collected and
   nothing observes a database that is about to close.
2. **Close**, then **wait** for those executors to terminate. This is the step
   nothing before had, because it was not possible with the shared pool.
3. Only then may the caller write `Dispatchers.resetMain()`.

Both constraints on the executors were learned by breaking them: they must be
**different objects**, because `RoomDatabase.Builder.build()` copies the query
executor into the transaction executor when only the former is given
(RoomDatabase.kt:1252, Room 2.6.1) and SQLite transactions are thread-bound; and
**neither may be same-thread**, because Room posts the invalidation refresh to the
query executor expecting it to run after the write commits.

Pinned by `TestDatabaseTeardownTest`, which asserts the executors are terminated
after `closeAndDrain` and *not* terminated after a plain `close()`. Mutation
checked: removing the wait fails it.

### Cause 3, fixed: a missed SharedFlow emission

`completeWorkoutEmitsEventAndBecomesReadOnly` collected `events`, a `SharedFlow`
with no replay, in a launched job and then asserted on the list. A collector that
had not started before `completeWorkout` emitted missed the value permanently.
Seen once in nine runs on 2026-08-09, recorded here at the time as *not* matching
the flake fingerprint, and fixed by awaiting the event with `async` so there is no
window between subscribing and emitting.

### Measurements

Full suite, `--rerun-tasks` between every run.

| Variant | Result |
|---|---|
| Baseline, as originally documented | ~1 in 4 |
| Baseline, re-measured 2026-08-08 | 1 in 10 |
| Cause 1 fixed only | 2 in 16 |
| Cause 1 + close-before-reset | 1 in 40 |
| **Baseline re-measured 2026-08-10, same machine** | **failed on runs 4 and 5** |
| **All three causes fixed** | **0 in 100** |

At the measured 1-in-5 baseline, 100 consecutive passes by chance has probability
about 2 in 10 billion.

### Nine approaches that failed, kept for the record

Every one of them tried to make teardown safe while the collectors were still
live, which the cause trace shows is impossible: cancelling reads the Main
delegate, draining dispatches on it, and stopping Room hard enough to silence it
makes the collectors throw.

1. A `ViewModelStore` per class, cleared in teardown. No measurable change.
2. Clearing, then `advanceUntilIdle()`. **8 in 8.** Draining is itself work on the
   dispatcher being removed.
3. Cancelling the leaked rest-timer scopes. 3 in 10.
4. A same-thread Room query executor. **Deadlocks**: it also becomes the
   transaction executor.
5. Not calling `resetMain` at all. **10 in 20**, and it leaked a dispatcher into
   later classes.
6. Separate single-thread query and transaction executors, query same-thread.
   6 failures on the first run: invalidation runs before the commit and never
   emits.
7. Cancel, then close, then reset, with no wait. 3 failures on the first run.
8. Drainable executors without cancelling first. Live collectors observe a closed
   database and throw into the next class.
9. `advanceTimeBy` past the `WhileSubscribed` timeout. **9 in 40.** Bounded or
   not, advancing the scheduler runs pending work that touches Main.

Approaches 4, 6 and 8 were each *half* of what finally worked. The missing half
was always cancelling and waiting together.

### What is deliberately kept

**The CI retry stays**, configured in `android/app/build.gradle.kts`. It no longer
exists for this entry, and it hides nothing: a retried pass is reported as
**FLAKY**, never green, and `maxFailures` still fails a broadly broken suite
outright. Keeping it means a future flake is counted rather than discovered by
someone re-running a red build by hand.


---

## TD-001 - Framework exceptions returned 500 instead of their proper status

Status: **Resolved 2026-08-07.**

Milestone identified: Backend Foundation (Milestone 1)
Resolved: 2026-08-07

### What was wrong

The generic `@ExceptionHandler(Exception.class)` in `GlobalExceptionHandler`
caught exceptions Spring MVC raises before a controller is reached and mapped all
of them to **500 Internal Server Error**, logged at ERROR as "An unexpected error
occurred."

That is wrong twice over. The caller is told the server broke when their request
was malformed, and ordinary client mistakes appear in monitoring as backend
faults, which is the noise that hides a real incident.

### What it cost

Two measured incidents, not a theoretical concern:

* **6 Aug 2026.** A request to `GET /api/v1/app/latest-version` during a Render
  deploy reached the previous container, where the route did not yet exist.
  Spring raised `NoResourceFoundException` and the handler returned 500. The
  deploy was investigated as a code defect until the timestamps showed the old
  container had served it. A 404 would have pointed at the deploy window
  immediately.
* **7 Aug 2026.** The first test ever written for `GET /api/v1/exercises/{id}`
  sent a malformed UUID and got a 500. That is how this was picked up again.

### The fix

Explicit handlers for the framework exceptions, each returning the status the
HTTP spec calls for:

| Exception | Was | Now |
|---|---|---|
| `NoResourceFoundException` (unmapped path) | 500 | **404**, logged at WARN |
| `HttpRequestMethodNotSupportedException` | 500 | **405**, with an `Allow` header |
| `MethodArgumentTypeMismatchException` (bad UUID) | 500 | **400** |
| `HttpMediaTypeNotSupportedException` | 500 | **415** |
| `MissingServletRequestParameterException` | 500 | **400** |

The catch-all remains for genuinely unexpected exceptions, which is what it was
always for.

### Covered by

`FrameworkErrorContractTest`, nine tests. It asserts the status, the error
envelope and the `Allow` header, and includes a case pinning the exercise search
parameter as optional so the missing-parameter handler cannot start rejecting it.


---

## TD-002 — Potential repository query for category filtering/ordering

Status: Open — note only (no action)

Milestone identified: Backend Exercise Library (Milestone 2)
Scheduled for: revisit only when a second consumer appears

### Note

ExerciseCategoryServiceImpl.getAllCategories() currently calls the inherited
findAll(), then filters soft-deleted records and orders by displayOrder in
memory at the service layer. This is intentional and correct while categories
are the only consumer and the table is small.

If multiple services eventually require identical filtering/ordering semantics
for categories, the repository may evolve to expose an explicit derived query
(for example findByIsDeletedFalseOrderByDisplayOrderAsc) so the semantics are
defined once and reused.

Until a genuine second consumer exists, the repository stays unchanged and the
logic remains in the service. YAGNI is the governing principle.

---

## TD-003 — Backend history endpoint scans the whole table

Status: ✅ Resolved 2026-07-22 (M10 prerequisites)

Milestone identified: Milestone 10 planning (2026-07-22)
Scheduled for: before M10 Phase 2 (the web history list consumes this endpoint)

### Observation

`WorkoutSessionServiceImpl.getHistory()` calls `findAll()`, then filters out
IN_PROGRESS sessions and sorts by `startedAt` **in Java**:

```java
return sessionRepository.findAll().stream()
        .filter(session -> session.getStatus() != WorkoutStatus.IN_PROGRESS)
        .sorted(Comparator.comparing(WorkoutSession::getStartedAt).reversed())
        .toList();
```

Every session row is loaded into memory on every request. The `WorkoutSession`
table is indexed on both `status` and `startedAt`; neither index is used.

### Why this is currently acceptable

V1 is single-user and the dataset is small (a heavy lifter produces a few hundred
sessions a year, and the summary payload is tiny). Nothing is slow today.

### Recommended implementation

A derived or `@Query` finder that filters and orders in SQL. This also makes
pagination trivial to add later, which the current shape does not.

### Resolution

`WorkoutSessionRepository.findByStatusOrderByStartedAtDesc(status, Pageable)`
replaces the `findAll()` + Java stream. The `Pageable` parameter is what makes
pagination a caller-side change rather than a rewrite; the service passes
`Pageable.unpaged()` today.

One assumption above was wrong and worth recording: the table was **not** indexed
on `status` — V1 indexed `startedAt` alone. Flyway `V5` adds the composite
`("status", "startedAt" DESC)` that this query actually needs, in the statement's
own column order and direction.

---

## TD-004 — WorkoutSet deletions never reach the backend

Status: ✅ Resolved 2026-07-22 (M10 prerequisites) — see ADR-0007

Milestone identified: Milestone 9 Phase 2 (deferred twice, by agreement)
Scheduled for: before M10 ships (it becomes user-visible there)

### Observation

`Routine` and `RoutineExercise` are soft-deleted, which was believed to let the
synchronization engine see and propagate the deletion. **That was not true** —
see TD-011, raised in M11 when real-device data disproved it. `WorkoutSet` is
**hard-deleted**: once
the row is gone locally there is no record that it ever existed, so the backend
is never told. `WorkoutRepositoryImpl.deleteSet` therefore deliberately does not
request a sync — there would be nothing to upload.

### Impact

The backend retains sets the device has deleted. This is invisible while Android
is the only client. It stops being invisible in M10: the web history view would
display sets that the phone does not, for the same user and the same workout.

### Options compared (M9 Phase 2 planning)

| Approach | Complexity | Storage | Multi-device |
|---|---|---|---|
| Tombstone table | Medium — one entity, one DAO, deletes must write two tables transactionally | Smallest; rows removed after upload | Best — an append-only delete log is what a future pull-sync needs |
| Soft-delete flag on WorkoutSet | Lowest; consistent with existing entities | Rows never leave the DB | Contaminates every history read path with `isDeleted = 0` |
| Pending-delete queue | Highest — a second sync mechanism alongside the status column | Small | Overkill for one-way V1 sync |

### Recommendation

Tombstone table. The soft-delete flag is cheaper today but adds a filter to every
history query, and history correctness is this project's highest priority.

Record the outcome as an ADR (ADR-0007) once decided: it changes synchronization
semantics, not just an implementation detail.

### Resolution

The tombstone table was chosen and implemented; **ADR-0007** records the decision
and the alternatives. Room v4 adds `workout_set_tombstone` (additive migration
`MIGRATION_3_4`), `WorkoutSetDao.deleteAndRecord` writes the delete and the
tombstone in one transaction, and the sync engine gained a deletion phase that
runs after the set uploads and before the session's terminal transition.

Verified against a live backend and PostgreSQL: a set uploaded and then deleted
is absent from the backend's snapshot while its sibling remains.

---

## TD-005 — No physical-device verification

Status: ✅ Resolved 2026-07-22 (M11 Phase 1)

Milestone identified: Milestone 9 finalization
Scheduled for: M11, before M12 release

### Observation

All Android runtime verification has been performed on an emulator (Pixel 6,
API 35). The application has never run on physical hardware.

Emulator verification did prove the parts most likely to differ from JVM tests:
`SyncWorker` executing in a real process, Hilt worker-factory injection, the
platform cleartext policy, and synchronization to PostgreSQL over a LAN address
(`192.168.1.7`) rather than the emulator loopback — the same routing a phone
would use.

### What remains unverified

A phone joining Wi-Fi and reaching the host; real-device performance, battery
behaviour under WorkManager, and doze-mode effects on background sync; behaviour
across manufacturers' background-execution restrictions, which are stricter than
stock Android.

### Recommended implementation

One full pass of the core flows on a physical device, including a background sync
after the screen has been off long enough for doze to apply.

### Resolution

Performed on a OnePlus CPH2717 (Android 16, ColorOS) in M11 Phase 1. Verified:
the Room v3→v4 migration against real training data; the 313-exercise library on
the device; workout sync fidelity (all four sessions matched device↔PostgreSQL on
status and set count); doze deferral (`WAIT:DEV_NOT_DOZING`, recovering cleanly on
exit); absence of ColorOS background restrictions (`RUN_ANY_IN_BACKGROUND
allowed`, standby bucket ACTIVE); and scroll performance (4.31% janky over 209
frames, 90th percentile 14 ms).

Instrumented tests were later confirmed to pass on the same hardware, identically
to the emulator (M11 Phase 3).

**Still unverified, and deliberately so:** long-idle ColorOS behaviour. The
standby bucket was ACTIVE because the app is in daily use; the real risk appears
only after days of disuse and cannot be fast-forwarded with a command. Reconsider
if background sync is ever reported as unreliable after a break from training.

---

## TD-006 — No release signing configuration

Status: ✅ Resolved 2026-07-22 (M12 Phase 1)

Milestone identified: Milestone 9 finalization
Scheduled for: M12 (Version 1 Release)

### Observation

`android/app/build.gradle.kts` defines no `signingConfig`. `assembleRelease`
produces `app-release-unsigned.apk`, which cannot be installed on any device.

There is therefore currently **no build that can be distributed to anyone**,
including the developer's own phone (which uses the debug build).

### Recommended implementation

A release signing config sourced from `local.properties` or environment
variables — never committed keystore credentials — plus a documented release
procedure in the README.

### Resolution (M12 Phase 1)

Signing credentials are read from `local.properties` or `MFL_*` environment
variables; the keystore (RSA 4096, valid to 2056) lives outside the repository
and `.gitignore` refuses `*.jks`, `*.keystore` and `keystore.properties`.
Missing credentials produce an unsigned APK **with a loud warning**; partial
credentials fail the build.

`versionCode` is now derived from a single tracked `versionName`
(`android/version.properties`), so the two cannot disagree and no one has to
remember to bump a second number.

Verified on the artifact rather than in the source: `apksigner verify` reports
`Verifies` with v2 and v3 true (v1 is correctly absent at `minSdk 26`), and the
release build ran the complete workflow — library download, routine creation,
workout logging, sync — with the data confirmed in PostgreSQL.

Procedure recorded in `docs/RELEASE_CHECKLIST.md`.

### Related — the HTTPS question, resolved differently than expected

This entry previously stated that "a real release requires a deployed backend
behind TLS." The M12 review decided otherwise: **Version 1 is a local production
release** (Option B) — a signed APK against a LAN backend, with no hosting, TLS
or authentication, all of which belong to V2.

The release build therefore does need a cleartext exemption, and it is generated
at build time scoped to the **single host** `apiBaseUrl` names, denying
cleartext everywhere else. It is deliberately far narrower than the debug config
(which permits any host, acceptable only because debug builds are never
distributed). When `apiBaseUrl` becomes an `https://` URL the generator emits a
config granting no exemption at all, so the relaxation removes itself rather
than depending on anyone remembering it.

Deploying behind TLS remains documented but unexecuted; see the M12 plan §4.

---

## TD-007 — Backend has no CORS configuration

Status: ✅ Resolved 2026-07-22 (M10 prerequisites)

Milestone identified: Milestone 10 planning (2026-07-22)
Scheduled for: before M10 Phase 2

### Observation

Nothing in `backend/src/main` configures CORS (no `addCorsMappings`,
`@CrossOrigin`, or `CorsConfigurationSource`). A browser client served from a
different origin — the Vite dev server on `:5173` — fails at the preflight
request, so **no web request succeeds at all** until this exists.

### Recommended implementation

A `WebMvcConfigurer` permitting the development origin and, later, the deployed
web origin. `GET` only and no credentials for V1, since the web client is
read-only and there is no authentication.

### Resolution

`CorsConfig` (a `WebMvcConfigurer`) maps `/api/**` with `GET` only, no
credentials, and origins from `app.cors.allowed-origins` — defaulting to the Vite
dev server, overridable per deployment, and disabled entirely when empty. A
hardcoded origin was avoided so the deployed web origin needs no code change.

Documented in API_SPECIFICATION §5b and verified both by test and by a real
preflight over the wire (allowed origin → 200 with `Allow-Methods: GET`; unknown
origin and write verbs → 403).

---

## TD-008 — History means different things to Android and the backend

Status: ✅ Resolved 2026-07-22 (M10 prerequisites)

Milestone identified: Milestone 10 planning (2026-07-22)
Scheduled for: before M10 Phase 2

### Observation

The two clients disagree on what "history" contains:

* Android (`WorkoutHistoryDao`): `WHERE status = 'COMPLETED'` — DISCARDED
  workouts are deliberately hidden from the user.
* Backend (`WorkoutSessionServiceImpl.getHistory()`): filters out only
  IN_PROGRESS, so **DISCARDED sessions are returned**.

A web client rendering that endpoint verbatim would show abandoned workouts that
the phone hides — the same user seeing two different histories.

### Decision (2026-07-22)

The **backend filters to COMPLETED**, matching Android and ADR-0001. History gets
one definition across every client; duplicating the filter in each frontend is
maintenance burden that will eventually drift. A `?status=` parameter remains a
reasonable future enhancement if discarded workouts ever become user-visible.

### Recommended implementation

Fold into TD-003 — the same query is being rewritten to filter in SQL.

### Resolution

Folded into TD-003 as planned: `getHistory()` now queries
`findByStatusOrderByStartedAtDesc(COMPLETED, …)`, so the filter is one SQL
predicate rather than a rule each client repeats. Recorded in
API_SPECIFICATION §7 (Workout Sessions) as part of the endpoint contract.

Note the small contract change this implies for existing consumers: the endpoint
previously returned DISCARDED sessions and no longer does. Android is unaffected —
it reads history from Room, and its DAO already filtered to COMPLETED.

---

## TD-009 — RoutineEntity lacks description and displayOrder

Status: Open — note only

Milestone identified: Milestone 9 Phase 1
Scheduled for: whenever routine descriptions or manual ordering reach the UI

### Observation

The backend's routine contract accepts optional `description` and `displayOrder`.
The Room `RoutineEntity` has neither, because no screen in ANDROID_FLOW edits
them, so the sync mappers send both as null. This is contract-valid — both are
optional server-side — but routine ordering cannot round-trip.

### Recommended implementation

Add the columns (with a Room migration, per CODING_STANDARDS §20b) at the same
time the UI gains the corresponding controls, not before.

---

## TD-010 — Workout history is not paginated

Status: Open — deferred

Milestone identified: Milestone 10 Phase 4 (2026-07-22)
Scheduled for: post-V1, or whenever a real dataset makes it noticeable

### Observation

`GET /api/v1/workout-sessions` returns **every** completed session in one JSON
array, and the web client renders all of them in one list. There is no `?page=`
or `?size=` parameter and no windowing on the client.

### Why this is currently acceptable

V1 is single-user. The current dataset is 21 sessions; a heavy lifter produces a
few hundred a year, and the summary payload is small (six fields, no nested
data). Nothing is slow today, and the M10 measurements bear that out.

### Impact as it grows

Two separate costs, neither urgent:

* The response grows linearly and is transferred in full on every load.
* Rendering cost grows with it. The `Intl` formatter caching added in Phase 4
  removed the sharpest edge here — formatting a 500-row list went from ~23 ms
  (over a 16 ms frame budget) to ~0.6 ms — but the DOM node count still grows.

### Recommended implementation

Backend first: add `?page=` / `?size=` to the history endpoint. TD-003 already
rewrote the query to take a `Pageable`, so the repository layer needs no change —
only the controller and service signatures, plus the API specification.

The web client is already shaped for it: `workoutKeys.history()` is a function so
params can join the query key, and `WorkoutHistoryList` is a pure component that
takes an array, so a paginated page passes one page's worth without the list or
card changing. Client-side windowing is a separate, later option if a single
page ever becomes large enough to matter.

### Documentation that must change first

* API_SPECIFICATION.md §7 — document the pagination parameters and the response
  shape (array vs envelope). The choice between the two is the real decision;
  an envelope is a breaking change for the Android client, which does not read
  this endpoint today but might under a future pull-sync.


---

## TD-011 — Routine and RoutineExercise deletions never reached the backend

Status: ✅ Resolved 2026-07-22 (M11 Phase 2) — see ADR-0007 §Amendment

Milestone identified: Milestone 11 Phase 1 (physical-device verification)

### Observation

Found in live data on a physical device, not by code review. A routine exercise
removed at 23:38 was still `PENDING` after six later sync passes, and still
present on the backend. For one routine the device showed 7 exercises and
PostgreSQL held 8.

### Cause

`RoutineDao.getPendingSync()` and `RoutineExerciseDao.getPendingSync()` filtered
`WHERE isDeleted = 0`, excluding precisely the rows whose deletion needed
uploading, and the sync engine had no delete phase for these entities — although
`RoutineApi.deleteRoutine` and `deleteRoutineExercise` already existed unused.

The DAO comment described this as deferring propagation "to a later phase".
Excluding the row did not defer the deletion; it discarded it.

### Why this mattered

It silently and permanently diverged the backend from the phone for every routine
edit, and it falsified a premise stated in this register (TD-004) and in
ADR-0007 — the very contrast used to justify tombstones for `WorkoutSet`.

Not user-visible while nothing read routines back, which is exactly why it
survived review: every test passed, because a test asserted the buggy behaviour.

### Resolution

The pending queries no longer filter on `isDeleted`; the engine dispatches on it
and sends a DELETE for soft-deleted rows, within the existing routine phases. No
new synchronization mechanism was added. A DELETE returning 404 counts as
success, and a pending child create is skipped when its parent routine was
deleted in the same pass.

Verified against a live backend and PostgreSQL, and on the physical device where
the original stuck row became `SYNCED` and the backend row disappeared —
device 7, backend 7.

---

## TD-012 — Reference-data deletions are reconciled, but only when unreferenced

Status: Open — note only (accepted behaviour, recorded for clarity)

Milestone identified: Milestone 11 Phase 2

### Note

`refreshLibrary()` now removes catalogue rows the backend no longer serves
(previously it only upserted, so a withdrawn exercise lingered forever — the
reason Flyway V6 renamed a category in place rather than removing it).

Rows still referenced by a routine or by workout history are **deliberately
kept**: `RoutineExercise` and `WorkoutExercise` hold RESTRICT foreign keys to the
catalogue, and a withdrawn exercise appearing in a past workout is part of that
immutable record (ADR-0001). It stops being offered for new work but remains
resolvable.

The consequence worth knowing: a withdrawn exercise can persist on a device
indefinitely if any workout references it. That is correct, not a leak, but it
means the local catalogue is not always a strict subset of the server's.

---

## TD-013 — Live sync tests write into the production database

Status: ✅ Resolved 2026-07-22 (M12 Phase 3) — elevated to a V1 release blocker
by the Phase 2 review, then fixed by environment separation

Milestone identified: Milestone 12 Phase 2 (2026-07-22)
Scheduled for: before the V1 release tag

### Observation

`LiveBackendSyncTest` is guarded by
`assumeTrue(backendIsUp())`, which probes `http://localhost:8080/api/v1/health`.
Its doc comment states it "is not part of the normal suite — CI and everyday
runs have no backend."

That assumption does not hold on this project's own machine. The backend has
been running locally since M9.5 dogfooding began, so the probe succeeds and the
test executes as part of an ordinary `./gradlew :app:testDebugUnitTest` — writing
routines, sessions and sets into `myfitnesslog`, the database ADR-0003 designates
as the system of record.

Measured during M12 Phase 2:

| | Test artifacts | Real | Total |
|---|---|---|---|
| Routines | **71** | 4 | 75 |
| Sessions (via test routines) | **57** | — | 75 |

**95% of the routines in the production database are test data.** The four real
routines are `Push`, `Pull`, `Device Validation Push` and the M12 verification
routine.

### Why this matters

It is not merely untidy. The backend is the only permanent copy of workout
history (sync is one-way), and it is what the web client renders and what the
M12 backup procedure preserves. Test data inflates every count anyone reasons
about, and it means a routine list can never be read at face value.

Nothing has been *lost* — the real rows are intact and identifiable — so this is
debt, not a defect.

### Root cause

The gate asks the wrong question. `backendIsUp()` distinguishes *"a backend is
reachable"* from *"no backend is reachable"*. What the test actually needs to
know is *"is this backend disposable?"* — and reachability is not evidence of
that. The guard was written when no backend ran locally, so the two questions
happened to have the same answer; dogfooding silently separated them.

This is the M11 pattern again: a safeguard that encodes an assumption about the
environment rather than checking the property it actually cares about, and which
therefore stops protecting anything the moment the environment changes.

### Recommended implementation

Any of these closes it; the first is preferred:

1. **Point live tests at `myfitnesslog_test`**, which already exists for backend
   integration tests. The system of record then cannot be written by a test run
   at all — structural rather than conventional.
2. Require an explicit opt-in (`-PliveSyncTests=true`), so running them is a
   decision rather than a side effect of the environment.
3. Have the tests clean up the rows they create — weakest, since a failed or
   interrupted run leaves them behind.

Existing test rows should be removed only with the project owner's approval;
they are in the owner's system of record, and M12 Phase 2 deliberately left
them in place.

### Documentation to change first

`CODING_STANDARDS.md` (testing section) should state that no test may write to
the development/production database, and `TESTING` guidance in the README should
say which database live tests use.

### Resolution (M12 Phase 3)

Fixed by separating environments, not by detecting bad ones. Two independent
conditions must now both hold before any live test runs:

1. **An explicitly configured target.** `MFL_LIVE_TEST_BASE_URL` (Android) and
   `VITE_LIVE_TEST_BASE_URL` (web), with **no default**. Unset means skip, so a
   merely-running backend can no longer cause a write.
2. **The backend declares itself disposable.** `GET /api/v1/health` returns
   `disposable`, which is `true` only under the new `livetest` Spring profile
   (port 8081, database `myfitnesslog_livetest`). Anything else — including a
   backend predating the field, where it is simply absent — is treated as
   precious, and the test **fails** rather than skipping, because the target
   answered but is not throwaway.

The second condition is what makes this structural rather than procedural: the
environment states what it is, instead of the client guessing. Pointing a live
test at the system of record now cannot write to it, whatever URL is supplied.

Verified by running all three paths and checking row counts either side:

| Configuration | Result | Production `Routine` |
|---|---|---|
| No variable set | 5 skipped | 76 → 76 |
| → production (8080) | **5 failures**, refused | 76 → 76 |
| → livetest (8081) | 5 passed | 76 → 76 (livetest 0 → 5) |

Suites after the change: Android 336 passed / 5 skipped, backend 92 passed, web
129 passed / 4 skipped. The skips are precisely the live tests that previously
ran against production by default.


---

## TD-014 — WorkoutExercise removal during a workout is not propagated to the backend

Status: ✅ Resolved 2026-08-07 (ADR-0017 Stage 2 prerequisite)

Milestone identified: V2 workout logging, Milestone F (2026-07-27)
Resolved by: `workout_exercise_tombstone` (Room migration 5 → 6) plus a deletion
phase in the sync engine, mirroring the set tombstones of ADR-0007.

### Why it stopped being cosmetic

This item was filed as a bounded, one-directional divergence with no user-visible
impact, and that assessment was correct **while synchronisation only ran upwards**.
Adding the read path changed its severity rather than its mechanics: once the phone
reads the backend back, a refresh downloads the lingering exercise and
**resurrects on the device an exercise the user deleted**. A cosmetic residue on a
write-only system becomes a correctness bug the moment that system gains a second
direction, which is why this was fixed before Stage 2 rather than alongside it.

The deletion phase is ordered deliberately: after the set deletions, because the
backend refuses to remove an exercise that still has sets; and before the terminal
transition, because a sealed session rejects every write. Refresh additionally
defers entirely while any tombstone is unreplayed, so the resurrection window is
closed from both ends.

### Observation

V2 workout logging lets a user remove an exercise from an **in-progress** session.
The removal deletes the exercise's **sets** with tombstones (ADR-0007) and those
set deletions propagate correctly. The `WorkoutExercise` **row itself** is removed
locally but its deletion is **not** sent to the backend: there is no
`WorkoutExercise`-level tombstone or soft-delete, and the sync engine has no delete
phase for it. Because a session's exercises are uploaded early (a session is synced
on start), an exercise that was added, synced, and then removed can remain on the
backend.

### Why this exists

Milestone F was deliberately kept schema-free (no Room migration): adding a
`WorkoutExercise` tombstone would have required a new table, a migration, and a new
sync path. Instead the milestone reused the existing, tested **set** tombstone path
for the exercise's sets and deferred the row-level propagation. This is recorded as
DEC-14 in the V2 decision log.

### User-visible impact

None on Android — the phone reads its own local state, where the exercise is gone.
The divergence is only observable by a second client that reads the session back
from the backend (today, the read-only web client): it could show an empty exercise
that the phone no longer lists, and only for a session that has not yet been
completed.

### Synchronization impact

A bounded, one-directional divergence: the backend retains an already-synced
`WorkoutExercise` row (now with no sets) that the phone has removed. It affects only
the removed exercise on a still-`IN_PROGRESS` session; set-level deletions and
exercise reordering both converge correctly.

### Why data integrity is preserved

Nothing is lost or corrupted. The lingering row is **empty** (its sets were
tombstoned and removed), and immutable workout **history** is unaffected: history is
built from completed sets (ADR-0001), and a removed exercise contributes none. The
residue is a cosmetic, non-authoritative extra row on a not-yet-finished session,
not a divergence in performed facts.

### Expected future resolution

Add a `WorkoutExercise` tombstone (or soft-delete) plus a sync delete phase,
mirroring the `WorkoutSet` mechanism in **ADR-0007** — a self-contained additive
change (Room migration + one sync phase). This also closes the matching stale note
amended in ADR-0007. A future bidirectional/pull sync would subsume it.
