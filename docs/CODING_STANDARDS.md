Coding Standards

Project: MyFitnessLog
Version: 1.0
Status: Approved — in force through Version 1.0.0 (22 July 2026)
Last Updated: July 20, 2026

⸻

1. Purpose

This document defines the coding standards and development conventions for MyFitnessLog.

The goal is to ensure that all code is:

* Consistent
* Readable
* Maintainable
* Testable
* Predictable

All manually written code and AI-generated code must follow these standards.

⸻

2. General Principles

The project follows these principles:

* Readability over cleverness
* Simplicity over premature optimization
* Composition over inheritance
* Explicit over implicit
* Small classes
* Small methods
* Clear responsibilities

Every class should have a single, well-defined responsibility.

⸻

3. Clean Code Guidelines

Always prefer:

* Meaningful names
* Small methods
* Immutable objects where practical
* Constructor injection
* Early validation
* Guard clauses

Avoid:

* Deep nesting
* Long methods
* Long parameter lists
* Duplicate logic
* Magic numbers
* Commented-out code

Comments should explain why, not what.

⸻

4. Project Structure

backend/
└── src/main/java/com/myfitnesslog
    ├── config
    ├── controller
    ├── dto
    │   ├── request
    │   └── response
    ├── entity
    ├── exception
    ├── mapper
    ├── repository
    ├── service
    ├── validation
    └── util

Future feature-based packaging may be adopted, but Version 1 uses a layered structure.

⸻

5. Package Naming

Package names:

* lowercase
* singular
* descriptive

Examples:

controller
repository
service
entity
mapper
exception

⸻

6. Class Naming

Use PascalCase.

Examples:

WorkoutSession
WorkoutSessionController
WorkoutSessionService
WorkoutSessionRepository
WorkoutSessionMapper
WorkoutSessionRequest
WorkoutSessionResponse

Do not use abbreviations unless they are universally understood (e.g., DTO, UUID).

⸻

7. Method Naming

Method names should describe behavior.

Examples:

createRoutine()
updateRoutine()
deleteRoutine()
startWorkout()
completeWorkout()
findWorkoutById()

Avoid vague names:

process()
handle()
execute()
run()

⸻

8. Variable Naming

Variables use camelCase.

Prefer descriptive names.

Good:

workoutSession
exerciseOrder
targetRestSeconds

Avoid:

obj
temp
value
x
data

⸻

9. Controller Standards

Controllers should:

* Handle HTTP requests
* Validate input
* Delegate to services
* Return responses

Controllers must not:

* Contain business logic
* Access repositories directly

⸻

10. Service Standards

Services contain business logic.

Responsibilities include:

* Validation
* Business rules
* Coordination
* Transactions

Services should not contain HTTP-related code.

Services must not rely on lazy-loading outside an active transaction. All data required by an API response must be explicitly obtained within the service transaction and mapped to DTOs before returning (see ADR-0005).

⸻

11. Repository Standards

Repositories are responsible only for persistence.

They must not contain:

* Business rules
* Validation
* HTTP concerns

⸻

12. DTO Standards

Never expose JPA entities directly.

Use separate DTOs.

Naming:

CreateRoutineRequest
UpdateRoutineRequest
RoutineResponse

Separate request and response models.

⸻

13. Entity Standards

Entities represent database tables.

Rules:

* No controller logic
* No API annotations
* No business rules

Keep entities focused on persistence.

For entity timestamp auditing (createdAt / updatedAt), follow ADR-0006.

Entity and column names must match the Flyway schema verbatim, including quoted PascalCase table names and quoted camelCase column names. Hibernate is configured with PhysicalNamingStrategyStandardImpl so these identifiers are used exactly as written and are never transformed (for example to snake_case). The Flyway schema remains the source of truth; entities map to it.

⸻

14. Mapper Standards

Use MapStruct.

Responsibilities:

* Entity → Response DTO
* Request DTO → Entity

Business logic should never exist inside mappers.

⸻

15. Validation

Use Jakarta Validation.

Examples:

@NotNull
@NotBlank
@Positive
@Size

Validate at the API boundary.

⸻

16. Exception Handling

Use a centralized exception handler.

Create custom exceptions where appropriate.

Examples:

RoutineNotFoundException
WorkoutNotFoundException
ValidationException

Return consistent error responses.

⸻

17. Logging

Use SLF4J.

Log:

* Application startup
* Errors
* Synchronization events

Do not log:

* Passwords
* Secrets
* Sensitive user information

Use appropriate log levels:

* ERROR
* WARN
* INFO
* DEBUG

⸻

18. Transactions

Annotate service methods requiring database consistency with @Transactional.

Avoid placing transaction annotations on controllers or repositories.

⸻

19. Testing

Use:

* JUnit 5
* Mockito

Test:

* Service layer
* Repository queries
* Controllers (where appropriate)

Prefer meaningful test names:

shouldCreateRoutine()
shouldRejectInvalidWorkout()
shouldReturnWorkoutHistory()

19b. Tests must never touch the system of record

**No automated test may read or write the production database.** Not "should
not by default", not "unless a backend happens to be running" — never.

A test that talks to a real server must satisfy two conditions, both required:

1. **Its target is named explicitly, with no default.** An unset environment
   variable means the test skips. `MFL_LIVE_TEST_BASE_URL` (Android),
   `VITE_LIVE_TEST_BASE_URL` (web).
2. **That server declares itself disposable** via `disposable: true` from
   `GET /api/v1/health`, which only the backend's `livetest` profile does
   (port 8081, database `myfitnesslog_livetest`). If the server answers without
   declaring it, the test **fails** — it does not skip. Skipping would hide a
   misconfiguration that was about to write into real data.

Never infer disposability. Reachability, port number, hostname and "localhost"
are all properties of *where* a server is, not of *what it holds*. Only the
server knows that, so only the server may state it.

This rule exists because it was learned expensively. `LiveBackendSyncTest` was
gated on "is a backend reachable on localhost:8080", documented as safe because
"CI and everyday runs have no backend". That was true when written and silently
became false when M9.5 dogfooding put a backend on localhost permanently. By the
time anyone noticed, 71 of the 75 routines in the production database were test
artifacts (TD-013).

The general lesson, which outlives this instance: **a safeguard that encodes an
assumption about the environment stops protecting you the moment the environment
changes, and it does so without failing.** Check the property you actually care
about.

⸻

20. Flyway

Each database change must be implemented through a Flyway migration.

Naming convention:

V1__Initial_schema.sql
V2__Seed_exercise_categories.sql
V3__Seed_exercises.sql

Never modify an existing migration after it has been committed.

Create a new migration for every schema change.

⸻

20b. Room (Android database)

The Android database follows the same discipline as Flyway, for the same reason:
once the app holds real training history, a schema change that is not migrated
destroys data that may exist nowhere else — anything not yet synchronized is
gone permanently.

The database does NOT use fallbackToDestructiveMigration. A version bump without
a matching migration therefore fails loudly at startup instead of silently
recreating the database.

Every Room schema change must:

1. Bump the version in MyFitnessLogDatabase and commit the exported schema JSON
   that Room writes to app/schemas/. The export is not optional — without it,
   migrations cannot be validated.
2. Add a Migration to the MIGRATIONS array in core/data/local/Migrations.kt.
3. Add a case to MigrationTest that seeds realistic rows at the old version,
   migrates, and asserts THE DATA SURVIVED — not merely that the migration ran.

Never modify a committed schema JSON, and never edit a released Migration.

This repository has no CI, so MigrationTest is the only thing enforcing the
policy. It is an instrumented test and needs a running device — **the emulator**:

    ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest

⸻

20c. Running instrumented tests — never against your phone

`connectedAndroidTest` installs an app + test APK and **uninstalls both
afterwards**. An Android uninstall deletes the app's database, so pointing that
task at a daily-use device destroys real training data — and because
synchronization is one-way (SYNC.md §6), the backend cannot give it back. This
happened on 2026-07-22; two routines and four workout sessions were lost from the
phone.

The build now refuses: `connected*AndroidTest` and `uninstall*` fail with an
explanatory error when the target is not an emulator. That is a safety net, not
permission to ignore the rule.

To exercise instrumented tests on real hardware — which is worth doing, and they
pass there — install both APKs manually and invoke the runner directly. This
performs no uninstall:

    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell am instrument -w com.myfitnesslog.test/com.myfitnesslog.HiltTestRunner

Back up the database first regardless:

    adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db > backup.db

The override (`-PallowPhysicalDeviceTests=true`) exists for deliberate use on a
device holding nothing you would miss. It is not a shortcut.

⸻

21. Git Commit Messages

Use imperative, descriptive commit messages.

Examples:

Add workout session entity
Implement routine repository
Create exercise REST controller
Add Flyway migration for workout tables

Avoid:

fix
changes
update
misc

⸻

22. Code Reviews

Every change should be reviewed against:

* PRD
* Architecture
* Database design
* API specification
* Coding standards

Reject changes that violate documented architecture.

⸻

23. AI-Assisted Development

When using AI tools:

* Do not accept generated code without review.
* Ensure generated code follows project architecture.
* Verify naming conventions.
* Remove unnecessary complexity.
* Prefer explicit implementations over generated abstractions.

AI accelerates implementation but does not replace architectural review.

⸻

24. Definition of Done

Code is considered complete only when it:

* Compiles successfully
* Passes all tests
* Follows the architecture
* Uses the approved technology stack
* Adheres to these coding standards
* Includes appropriate documentation where necessary
* Maintains readability and consistency