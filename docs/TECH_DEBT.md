# Technical Debt Register

Project: MyFitnessLog
Version: 1.3
Last Updated: July 22, 2026 (M11 Track A complete; TD-005 resolved, TD-011/TD-012 added)

This document records known, accepted technical debt: deliberate limitations that are not defects in the current milestone but must be addressed in a later milestone. Each item states the observation, why it is currently acceptable, the recommended future implementation, the documentation that must change first, and when it is scheduled.

This register holds debt that outlives a single task. Short-lived working items live in the development TODO and are not duplicated here.

### Index

| ID | Item | Status | Blocks |
|---|---|---|---|
| TD-001 | 405 returned as 500 | Open — deferred | — |
| TD-002 | Category filtering in service, not repository | Open — note only | — |
| TD-003 | History endpoint scans the whole table | ✅ Resolved 2026-07-22 | — |
| TD-004 | WorkoutSet deletions never reach the backend | ✅ Resolved 2026-07-22 (ADR-0007) | — |
| TD-005 | No physical-device verification | ✅ Resolved 2026-07-22 | — |
| TD-006 | No release signing configuration | Open | **M12 (distribution)** |
| TD-007 | Backend has no CORS configuration | ✅ Resolved 2026-07-22 | — |
| TD-008 | History differs between Android and backend | ✅ Resolved 2026-07-22 | — |
| TD-009 | RoutineEntity lacks description/displayOrder | Open — note only | — |
| TD-010 | Workout history is not paginated | Open — deferred | post-V1 (scaling) |
| TD-011 | Routine deletions never reached the backend | ✅ Resolved 2026-07-22 | — |
| TD-012 | Reference data keeps referenced withdrawn rows | Open — note only | — |

---

## TD-001 — HTTP method-not-supported returns 500 instead of 405

Status: Open — deferred

Milestone identified: Backend Foundation (Milestone 1)
Scheduled for: Exercise Library (Milestone 3, first real CRUD endpoints)

### Observation

During Milestone 1 runtime verification, sending an unsupported HTTP method to a mapped endpoint (`POST /api/v1/health`, which is GET-only) returned:

* HTTP status: 500 Internal Server Error
* Body: the documented error envelope with message "An unexpected error occurred."

The generic `@ExceptionHandler(Exception.class)` in GlobalExceptionHandler catches Spring MVC's `HttpRequestMethodNotSupportedException` and maps it to 500.

### Why this is currently acceptable (not a Milestone 1 defect)

* API_SPECIFICATION.md does not currently define HTTP 405 handling.
* Milestone 1 was scoped to only the approved exception handlers:
  * MethodArgumentNotValidException
  * HttpMessageNotReadableException
  * IllegalArgumentException
  * Generic Exception
* No functional CRUD endpoints exist yet, so the condition is not reachable in normal Milestone 1 usage.

### Impact

* A client error (wrong method) is reported as a server error (5xx), which is semantically misleading for clients and monitoring.
* The same masking applies to other framework-raised MVC conditions (for example 404 no-handler, 415 unsupported media type, 406 not acceptable), which would also surface as 500.

### Recommended future implementation

* Make GlobalExceptionHandler extend Spring's `ResponseEntityExceptionHandler` so framework MVC exceptions receive their correct HTTP status.
* Override the relevant handler(s) so these responses still use the documented error envelope (timestamp, status, error, message, path).
* Ensure `HttpRequestMethodNotSupportedException` returns 405, while the generic `Exception` handler continues to return 500 only for genuinely unexpected failures.

### Documentation that must change first (documentation is the source of truth)

* API_SPECIFICATION.md — Section 10 (HTTP Status Codes): add 405 Method Not Allowed (and clarify handling of other framework 4xx conditions) and confirm they return the standard error envelope.

Implementation must not begin until the documentation above is updated and approved.

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

Status: Open — blocks distribution

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

### Related

The release build also has no HTTPS backend to point at: `API_BASE_URL` defaults
to a development address and release builds forbid cleartext (correctly). A real
release requires a deployed backend behind TLS.

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
