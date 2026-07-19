Architecture

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: July 19, 2026

⸻

1. Purpose

This document defines the software architecture for MyFitnessLog.

It establishes the system structure, module boundaries, data flow, architectural principles, and development guidelines.

Every implementation must follow this architecture unless a future architectural decision explicitly changes it.

⸻

2. Architectural Goals

The architecture is designed to achieve the following goals:

* Offline-first operation
* High maintainability
* Clear separation of responsibilities
* Modular design
* Simple deployment
* Easy testing
* Future scalability
* Low coupling
* High cohesion

⸻

3. System Overview

MyFitnessLog consists of three applications.

┌────────────────────────────┐
│      Android Application   │
└──────────────┬─────────────┘
               │
          HTTPS / REST
               │
┌──────────────▼─────────────┐
│      Spring Boot API       │
└──────────────┬─────────────┘
               │
         Spring Data JPA
               │
┌──────────────▼─────────────┐
│        PostgreSQL          │
└──────────────┬─────────────┘
               │
        HTTPS / REST
               │
┌──────────────▼─────────────┐
│      React Web App         │
└────────────────────────────┘

The backend is the single source of truth.

⸻

4. Architectural Principles

4.1 Offline First

The application must function without internet connectivity.

All workout operations should succeed while offline.

Synchronization happens later.

⸻

4.2 Backend as Source of Truth

Permanent synchronized data resides in PostgreSQL.

Android maintains a local cache.

The web application always retrieves data from the backend.

⸻

4.3 Modular Design

Every feature should be implemented independently.

Example:

* Routine
* Exercise
* Workout
* History

Each feature owns its UI, business logic, persistence, and API interactions.

⸻

4.4 Separation of Concerns

Each layer has a single responsibility.

Business logic should never exist inside the UI.

Database logic should never exist inside controllers.

⸻

5. Android Architecture

Android follows the MVVM architecture.

UI (Jetpack Compose)
        │
        ▼
ViewModel
        │
        ▼
Repository
       ├─────────────┐
       ▼             ▼
 Room Database   Retrofit API

UI

Responsible only for presentation.

No business logic.

⸻

ViewModel

Responsible for:

* UI state
* User actions
* Calling repositories

⸻

Repository

Responsible for:

* Reading local data
* Calling remote APIs
* Managing synchronization

Repositories hide implementation details from the ViewModel.

⸻

Room Database

Stores:

* Workout data
* Routines
* Exercises
* Pending sync operations

Room is the single source of truth on Android.

⸻

Retrofit

Communicates with the backend.

No business logic should exist inside API classes.

⸻

6. Backend Architecture

The backend follows a layered architecture.

Controller
      │
Service
      │
Repository
      │
PostgreSQL

⸻

Controller Layer

Responsibilities:

* Receive HTTP requests
* Validate input
* Return HTTP responses

Controllers should contain minimal logic.

⸻

Service Layer

Responsibilities:

* Business rules
* Validation
* Coordination between repositories

Most application logic belongs here.

⸻

Repository Layer

Responsibilities:

* Database access
* Queries
* Persistence

Repositories should never contain business rules.

⸻

Entity Layer

Represents database tables.

Entities should not be exposed directly to clients.

⸻

DTO Layer

Used for communication between the backend and clients.

Entities remain internal.

⸻

Mapper Layer

Responsible for converting:

Entity ↔ DTO

⸻

7. Web Architecture

React follows a feature-based organization.

Pages
↓
Components
↓
Services
↓
REST API

Pages compose reusable components.

Services communicate with the backend.

Components should remain presentation-focused.

⸻

8. Data Flow

Workout creation:

User
↓
Android UI
↓
ViewModel
↓
Repository
↓
Room Database
↓
UI Updated Immediately
↓
Sync Worker
↓
Backend API
↓
PostgreSQL

This ensures the user never waits for network operations.

⸻

9. Synchronization Architecture

Synchronization occurs in the background.

Workflow:

1. User records workout.
2. Workout saved locally.
3. Sync request queued.
4. WorkManager executes sync.
5. Backend confirms success.
6. Local sync state updated.

If synchronization fails:

* Data remains local.
* Retry automatically.
* Never discard unsynchronized data.

⸻

10. Error Handling

Android:

* Show user-friendly messages.
* Retry automatically where appropriate.
* Prevent data loss.

Backend:

* Return meaningful HTTP status codes.
* Return structured error responses.
* Log server-side exceptions.

⸻

11. Logging

Backend:

* Log requests
* Log errors
* Log synchronization events

Android:

* Debug logging during development
* Remove unnecessary logging for production

Sensitive information must never be logged.

⸻

12. Project Structure

Repository structure:

MyFitnessLog/
android/
backend/
web/
docs/
README.md
.gitignore

Each application manages its own internal structure independently.

⸻

13. Design Principles

The project follows these principles:

* Single Responsibility Principle
* Dependency Inversion Principle
* Composition over Inheritance
* DRY (Don’t Repeat Yourself)
* KISS (Keep It Simple)
* YAGNI (You Aren’t Gonna Need It)

Avoid premature optimization.

Build only what Version 1 requires.

⸻

14. Architectural Constraints

Version 1 intentionally avoids:

* Microservices
* Event-driven architecture
* Message queues
* GraphQL
* WebSockets
* CQRS
* Event sourcing
* Distributed transactions
* Complex caching layers

The application remains a modular monolith.

⸻

15. Future Expansion

The architecture should support adding:

* Authentication
* Multiple users
* Nutrition tracking
* Weight tracking
* Sleep tracking
* Health Connect
* AI insights

These additions should require new modules rather than rewriting existing ones.

⸻

16. Development Guidelines

Before implementing any feature:

1. Understand the feature requirements.
2. Follow the PRD.
3. Follow the Technology Stack.
4. Follow this Architecture document.
5. Keep the implementation simple.
6. Write clean, readable code.
7. Maintain separation of responsibilities.

No implementation should violate the architectural principles defined here.

⸻

17. Definition of Done

A feature is architecturally complete when:

* It follows the defined architecture.
* Responsibilities are correctly separated.
* Business logic is isolated from the UI.
* Offline-first behavior is preserved.
* Code is modular and maintainable.
* No unnecessary complexity has been introduced.