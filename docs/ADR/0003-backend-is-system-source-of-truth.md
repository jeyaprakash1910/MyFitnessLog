# ADR-0003: Backend is the System Source of Truth

## Status

Accepted

⸻

## Context

MyFitnessLog consists of three major components:

* Android Application
* Spring Boot Backend
* PostgreSQL Database

The Android application must support complete offline operation using a local Room database.

However, the system also requires a permanent, synchronized copy of all user data that can be accessed by other clients, such as the web application and future Android devices.

A clear ownership model is required to define which component ultimately owns synchronized data.

⸻

## Decision

The backend is the system source of truth.

The PostgreSQL database stores the authoritative synchronized copy of all persistent application data.

The Android Room database acts as a local working copy that enables offline functionality.

Once synchronization completes successfully:

* Android and backend contain equivalent data.
* The backend becomes the authoritative persisted state.
* Other clients always retrieve data from the backend.

The ownership model is therefore:

```text
Android (Room)
       │
       │ Local Changes
       ▼
Background Synchronization
       │
       ▼
Spring Boot API
       │
       ▼
PostgreSQL
       │
       ▼
System Source of Truth
```

⸻

## Consequences

### Benefits

* Clear ownership of synchronized data.
* Supports future web application development.
* Supports future multi-device synchronization.
* Simplifies backup and recovery.
* Provides a single location for reporting and analytics.
* Prevents divergent long-term data between clients.

⸻

Trade-offs

* Android and backend data may temporarily differ while synchronization is pending.
* Reliable synchronization becomes a critical responsibility.
* Backend APIs must be idempotent to safely process repeated synchronization requests.

These trade-offs are acceptable because uninterrupted offline usage has higher priority than immediate server consistency.

⸻

## Alternatives Considered

Android as Permanent Source of Truth

Store all permanent data only on the Android device.

Rejected because:

* No centralized backup.
* No web application support.
* Difficult multi-device synchronization.
* Risk of permanent data loss if the device is lost.

⸻

### Dual Source of Truth

Allow both Android and backend to independently own data.

Rejected because:

* Increased complexity.
* Difficult conflict resolution.
* Higher risk of inconsistent data.
* Unclear ownership responsibilities.

⸻

## Implementation Guidelines

* Android always writes to Room first.
* Synchronization sends pending changes to the backend.
* Backend persists synchronized data to PostgreSQL.
* Web application communicates only with the backend.
* Android never bypasses synchronization by directly modifying backend state outside the defined API.
* All persistent entities use UUIDs to ensure consistent identity across systems.

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
* ADR-0002: Room is the Android Source of Truth