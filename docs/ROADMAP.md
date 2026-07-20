Project Roadmap

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: July 20, 2026

⸻

1. Purpose

This roadmap defines the implementation plan for MyFitnessLog Version 1.

The objective is to build the application incrementally while maintaining a working codebase at every milestone.

Each phase builds upon the previous phase and should be completed before starting the next.

⸻

2. Development Principles

The implementation follows these principles:

* Build vertically rather than horizontally.
* Keep the application runnable after every milestone.
* Complete one feature before starting another.
* Test continuously.
* Commit small, meaningful changes.
* Avoid partially implemented features.

⸻

3. Milestone Overview

Milestone	Status
Foundation Documents	✅ Completed
Backend Foundation	✅ Completed
Backend Exercise Library	✅ Completed
Android Foundation	🔄 In Progress
Android Exercise Library	🔄 In Progress (built within M3 phases)
Routine Management	Pending
Workout Logging	Pending
Workout History	Pending
Synchronization	Pending
Web Application	Pending
Testing & Polish	Pending
Version 1 Release	Pending

⸻

4. Milestone 1 — Backend Foundation

Goal

Establish the backend project structure and infrastructure.

Deliverables

* Spring Boot project
* Maven configuration
* PostgreSQL connection
* Flyway integration
* JPA configuration
* Global exception handling
* Logging configuration
* Health endpoint
* OpenAPI / Swagger
* Initial project structure

Exit Criteria

* Application starts successfully.
* Database connection established.
* Flyway executes successfully.
* Swagger UI is available.
* Health endpoint responds successfully.

⸻

5. Milestone 2 — Backend Exercise Library

Goal

Implement the master exercise library.

Deliverables

Backend:

* Exercise entities
* Category entities
* Repositories
* Services
* Controllers
* Seed data

Exit Criteria

* Exercise library loads correctly.
* Search functions correctly.
* Categories display correctly.

⸻

6. Milestone 3 — Android Foundation

Goal

Create the Android application skeleton.

Deliverables

* Jetpack Compose project
* Navigation Compose
* Hilt dependency injection
* Room database
* Retrofit client
* WorkManager
* Repository layer
* Base ViewModels
* Theme
* Navigation graph

Exit Criteria

* Application launches successfully.
* Navigation works.
* Room database initializes.
* Dependency injection functions correctly.

Implementation Progress (as of July 20, 2026)

Delivered incrementally across five reviewed phases. The Android module lives in
`android/` (single Gradle module). Architecture is recorded in
docs/ANDROID_ARCHITECTURE.md.

* Phase 1 — Project skeleton: Application (Hilt), single Activity, Material 3
  theme, Navigation Compose graph (Home/Workout/History/Settings placeholders),
  DI modules (Database/Network/Dispatcher), Room infrastructure + converters,
  Retrofit/OkHttp/kotlinx.serialization, version catalog, Gradle wrapper. ✅
* Phase 2 — Room reference-data layer: ExerciseCategory/Exercise entities, DAOs,
  database, exported schema (committed). ✅
* Phase 3 — Network + repository boundary: Retrofit APIs, DTOs, hand-written
  mappers, repository interfaces + implementations, Hilt bindings. ✅
* Phase 4 — Exercise Library presentation: ExerciseListViewModel, immutable
  UI state, Compose screen, Home wired to the real screen, offline-first. ✅
* Phase 5 — Local search + category filtering: Room-only filtering, no backend
  search endpoint used. ✅

Verification: 37 automated tests pass (converters, DAO, repositories, ViewModel,
Compose UI), executed on the JVM via Robolectric.

Remaining for Milestone 3:

* WorkManager — not built. Deferred to Milestone 8 (Synchronization), where it
  belongs; it is not required to display data.
* On-device / emulator runtime verification of the exit criteria (launch,
  navigation, Room init, DI at runtime). Deferred: no emulator is installed;
  every layer is currently verified by executed tests rather than a device run.

Note: the Exercise Library screens (Milestone 4 scope — list, search, category
browsing) were implemented within these phases, ahead of the original plan.

⸻

7. Milestone 4 — Android Exercise Library

Goal

Implement the Android exercise library screens using the Backend Exercise Library APIs.

Deliverables

Android:

* Exercise repository
* Exercise list screen
* Exercise search
* Category browsing

Exit Criteria

* Exercise library loads correctly on Android.
* Search functions correctly.
* Categories display correctly.

Implementation Progress (as of July 20, 2026)

Delivered ahead of schedule within the Milestone 3 phases:

* Exercise repository (download + local cache). ✅
* Exercise list screen (Compose, offline-first). ✅
* Exercise search (local, Room-only). ✅
* Category browsing / filtering (local, Room-only). ✅

Remaining: on-device / emulator confirmation of the exit criteria (no emulator
installed yet). Behaviour is currently verified by automated tests.

⸻

8. Milestone 5 — Routine Management

Goal

Implement workout templates.

Deliverables

Backend:

* Routine APIs
* RoutineExercise APIs

Android:

* Home screen
* Routine details
* Create routine
* Edit routine
* Duplicate routine
* Delete routine

Exit Criteria

* Users can fully manage workout routines.
* Routine data persists correctly.
* Offline functionality is verified.

⸻

9. Milestone 6 — Workout Logging

Goal

Implement active workout tracking.

Deliverables

Backend:

* WorkoutSession APIs
* WorkoutExercise APIs
* WorkoutSet APIs

Android:

* Workout screen
* Workout timer
* Rest timer
* Add/edit/delete sets
* Complete workout
* Discard workout

Exit Criteria

* Complete workouts can be recorded.
* Workout data is stored locally.
* Historical snapshots are created correctly.

⸻

10. Milestone 7 — Workout History

Goal

Implement historical workout viewing.

Deliverables

Backend:

* Workout history endpoints
* Workout detail endpoints

Android:

* History screen
* Workout detail screen
* Read-only completed workouts

Exit Criteria

* Users can browse historical workouts.
* Workout details display accurately.
* Snapshot integrity is preserved.

⸻

11. Milestone 8 — Synchronization

Goal

Synchronize Android and backend data.

Deliverables

* WorkManager integration
* Sync queue
* Pending sync processing
* Retry mechanism
* Sync status tracking
* Idempotent backend endpoints

Exit Criteria

* Offline-created data synchronizes successfully.
* Retry mechanism functions correctly.
* No data loss occurs during synchronization.

⸻

12. Milestone 9 — Web Application

Goal

Build the web client for viewing workout history.

Deliverables

* React application
* Workout history page
* Workout detail page
* Responsive layout
* API integration

Exit Criteria

* Workout history matches backend data.
* Workout details render correctly.
* Responsive design works on desktop and tablet.

⸻

13. Milestone 10 — Testing & Polish

Goal

Improve quality and prepare for release.

Deliverables

Backend:

* Unit tests
* Integration tests

Android:

* UI testing
* Repository testing
* ViewModel testing

General:

* Performance improvements
* Bug fixes
* Documentation updates

Exit Criteria

* Critical bugs resolved.
* Core workflows verified.
* Documentation reflects implementation.

⸻

14. Version 1 Release Checklist

Before releasing Version 1:

* All milestones completed.
* All APIs documented.
* Database migrations verified.
* Android application stable.
* Backend stable.
* Web application stable.
* Documentation updated.
* GitHub repository organized.
* Release tag created.

⸻

15. Future Versions

Version 2

* Authentication
* Multi-user support
* User profiles
* Dashboard

⸻

Version 3

* Weight tracking
* Sleep tracking
* Water intake
* Progress photos

⸻

Version 4

* Health Connect
* Apple Health
* Wearable integration
* Cloud backup enhancements

⸻

Version 5

* Nutrition tracking
* Analytics dashboard
* AI insights
* Smart workout recommendations

⸻

16. Success Criteria

Version 1 is considered complete when:

* Users can create workout routines.
* Users can perform complete workouts offline.
* Workout history is permanently stored.
* Android synchronizes with the backend automatically.
* Workout history is viewable on the web.
* All documentation is consistent with the implementation.
* The application is stable, maintainable, and ready for future expansion.