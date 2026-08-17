# ADR-0002: Room is the Android Source of Truth

## Status

Accepted

⸻

## Context

MyFitnessLog follows an offline-first architecture.

The Android application must remain fully functional regardless of network availability.

Users should never experience delays while:

* Creating routines
* Editing routines
* Starting workouts
* Logging sets
* Completing workouts

If the UI depends directly on network responses, temporary connectivity issues could interrupt the workout experience and lead to inconsistent application state.

A clear data ownership model is required to ensure predictable behavior and simplify synchronization.

⸻

## Decision

The Room database is the single source of truth on the Android device.

All user actions are written to Room before any network communication occurs.

The UI observes only Room and never reads data directly from the backend.

Synchronization with the backend occurs asynchronously using WorkManager.

The backend updates do not modify the UI directly. Instead, successful synchronization updates Room if necessary, and Room propagates changes to the UI through observable data streams.

The data flow is therefore:

```text
User Action
      │
      ▼
 ViewModel
      │
      ▼
 Repository
      │
      ▼
Room Database
      │
      ▼
 UI Updates
      │
      ▼
WorkManager
      │
      ▼
REST API
      │
      ▼
Spring Boot
      │
      ▼
PostgreSQL
```

⸻

## Consequences

### Benefits

* Fully functional offline experience.
* Immediate UI updates without waiting for network requests.
* Consistent application state.
* Simplified synchronization logic.
* Reduced network dependency.
* Better user experience during workouts.
* Easier testing of business logic.
* Clear separation between local persistence and remote synchronization.

⸻

Trade-offs

* Local and backend data may differ temporarily until synchronization completes.
* Synchronization logic must correctly handle pending and failed operations.
* Additional storage is required on the device for local persistence.

These trade-offs are acceptable because uninterrupted workout logging is a higher priority than immediate server consistency.

⸻

## Alternatives Considered

Network-First Architecture

Every user action is sent directly to the backend before updating the UI.

Rejected because:

* Internet connectivity becomes mandatory.
* Poor network conditions degrade user experience.
* Workouts could be interrupted by server failures.
* Increased latency while logging sets.

⸻

Cache-Only Repository

The UI reads from either local storage or the backend depending on availability.

Rejected because:

* Multiple data sources complicate state management.
* Higher risk of inconsistent UI behavior.
* More complex testing and debugging.

⸻

## Implementation Guidelines

* The UI must never call Retrofit directly.
* ViewModels communicate only with Repositories.
* Repositories coordinate Room and network operations.
* All user-facing screens observe Room using Kotlin Flow.
* WorkManager is responsible for background synchronization.
* Retrofit responses should update Room rather than the UI directly.
* Synchronization failures must never prevent local data persistence.

⸻

## Related Documents

* PRD.md
* TECH_STACK.md
* ARCHITECTURE.md
* DATABASE.md
* API_SPECIFICATION.md
* SYNC.md

⸻

## Related ADRs

* ADR-0001: Workout History is the Source of Truth