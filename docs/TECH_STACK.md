Technology Stack

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: July 19, 2026

⸻

1. Purpose

This document defines the official technology stack for MyFitnessLog Version 1.

All future development, architecture decisions, and AI-generated code must follow the technologies and standards defined in this document unless explicitly changed through a documented architectural decision.

⸻

2. System Overview

MyFitnessLog consists of three primary applications:

* Android Application
* Backend API
* Web Application

The Android application is the primary client.

The backend acts as the single source of truth for synchronized data.

The web application is primarily used for viewing workout history and analytics.

⸻

3. High-Level Architecture

Android App
       │
 REST API (HTTPS)
       │
Spring Boot Backend
       │
 PostgreSQL
       │
React Web Application

⸻

4. Android Technology Stack

Component	Technology
Language	Kotlin
UI Toolkit	Jetpack Compose
Architecture	MVVM
Dependency Injection	Hilt
Local Database	Room
Networking	Retrofit
JSON Serialization	Kotlinx Serialization
Background Tasks	WorkManager
Navigation	Navigation Compose
Asynchronous Programming	Kotlin Coroutines
Reactive Streams	Kotlin Flow
Image Loading	Coil
Build System	Gradle (Kotlin DSL)

⸻

5. Backend Technology Stack

Component	Technology
Language	Java 21 (LTS)
Framework	Spring Boot
Build Tool	Maven
ORM	Spring Data JPA (Hibernate)
Database	PostgreSQL
Database Migrations	Flyway
API Style	REST
Validation	Jakarta Validation
Object Mapping	MapStruct
Logging	SLF4J + Logback
Testing	JUnit 5 + Mockito
API Documentation	SpringDoc OpenAPI (Swagger)

⸻

6. Web Technology Stack

Component	Technology
Language	TypeScript
Framework	React
Build Tool	Vite
Routing	React Router
HTTP Client	Axios
State Management	TanStack Query
Styling	Tailwind CSS

⸻

7. Database

Component	Technology
Database	PostgreSQL
Migration Tool	Flyway
Development Database	PostgreSQL (Docker or Local Installation)

The backend database is the single source of truth.

The Android application maintains a local Room database for offline functionality.

⸻

8. Data Synchronization

The application follows an Offline-First architecture.

Principles:

* All user actions are written to the local Room database first.
* The UI always reads from Room.
* Synchronization happens in the background.
* Failed synchronizations are automatically retried.
* The backend becomes the permanent synchronized copy of the data.

⸻

9. Architectural Pattern

Android

* MVVM
* Repository Pattern
* Use Case layer (if complexity grows)
* Single Source of Truth (Room)

⸻

Backend

* Controller
* Service
* Repository
* Entity
* DTO
* Mapper

Business logic must reside in the Service layer.

Controllers should remain thin.

Repositories should contain only persistence logic.

⸻

Web

* Component-based architecture
* Feature-oriented folder structure
* API access through dedicated service classes
* Server state managed with TanStack Query

⸻

10. API Communication

Communication between clients and the backend will use:

* REST APIs
* HTTPS
* JSON request/response bodies

Authentication is intentionally excluded from Version 1.

⸻

11. Development Environment

Required Software

* macOS
* Android Studio
* IntelliJ IDEA
* Visual Studio Code
* Java 21
* Kotlin
* Maven
* Node.js (LTS)
* PostgreSQL
* Git
* GitHub

⸻

12. Version Control

Source control:

* Git

Remote repository:

* GitHub

Branching strategy for Version 1:

* main only

Feature branches may be introduced in future versions if required.

⸻

13. Coding Principles

The project should prioritize:

* Readability over cleverness.
* Simplicity over premature optimization.
* Maintainability over short-term convenience.
* Composition over inheritance where practical.
* Small, focused classes and functions.
* Consistent naming conventions.
* Clear separation of responsibilities.

⸻

14. Future Technology Considerations

These technologies are intentionally deferred until later versions:

* Spring Security
* JWT Authentication
* OAuth / Google Sign-In
* Firebase
* WebSockets
* GraphQL
* Microservices
* Kubernetes
* Redis
* Message Queues
* Event Streaming

Version 1 should remain a well-structured monolithic application.

⸻

15. Technology Selection Rationale

Kotlin + Jetpack Compose

Modern Android development with excellent tooling, first-party support, and a declarative UI framework.

Spring Boot

Mature ecosystem, strong community support, and excellent integration with PostgreSQL and REST APIs.

PostgreSQL

Reliable relational database with excellent performance and scalability for structured workout data.

React + Vite

Fast development experience, lightweight tooling, and a large ecosystem for building responsive web interfaces.

Room

Provides robust offline storage and integrates naturally with Kotlin Coroutines and Flow.

WorkManager

Ensures reliable background synchronization even if the application is closed.

Retrofit

A mature and widely adopted HTTP client for Android with excellent integration into Kotlin applications.

⸻

16. Definition of Done

Every new feature added to the project must:

* Follow the technology stack defined in this document.
* Use the approved architectural patterns.
* Include tests where appropriate.
* Maintain compatibility with offline-first synchronization.
* Avoid introducing new frameworks without updating this document first.