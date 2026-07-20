API Specification

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: July 20, 2026

⸻

1. Purpose

This document defines the REST API contract for MyFitnessLog Version 1.

The API is consumed by:

* Android Application
* React Web Application

The backend is the single source of truth.

All clients must communicate exclusively through these REST endpoints.

⸻

2. API Principles

The API follows these principles:

* RESTful design
* Resource-oriented endpoints
* JSON request and response bodies
* Stateless communication
* Consistent naming
* Predictable HTTP status codes
* Client-friendly error responses

⸻

3. Base URL

Development

http://localhost:8080/api/v1

Production

https://your-domain/api/v1

All endpoints are versioned.

⸻

4. Content Type

Requests

Content-Type: application/json

Responses

Content-Type: application/json

⸻

5. Authentication

Version 1 intentionally has no authentication.

All requests are treated as belonging to the single application user.

Authentication will be introduced in a future version without changing endpoint structures.

⸻

6. Resource Overview

The API exposes the following resources:

Resource	Purpose
Exercises	Exercise library
Exercise Categories	Exercise grouping
Routines	Workout templates
Routine Exercises	Exercises inside a routine
Workout Sessions	Workout lifecycle
Workout Exercises	Historical exercise snapshots
Workout Sets	Recorded performance

⸻

7. Endpoint Summary

Exercise Categories

Method	Endpoint	Description
GET	/exercise-categories	List all categories

⸻

Exercises

Method	Endpoint	Description
GET	/exercises	List exercises
GET	/exercises/{id}	Exercise details
GET	/exercises/search	Search exercises (query parameter: q — case-insensitive name match)

⸻

Routines

Method	Endpoint	Description
GET	/routines	List routines
GET	/routines/{id}	Routine details
POST	/routines	Create routine
PUT	/routines/{id}	Update routine
DELETE	/routines/{id}	Soft delete routine
POST	/routines/{id}/duplicate	Duplicate routine

⸻

Routine Exercises

Method	Endpoint	Description
POST	/routines/{id}/exercises	Add exercise
PUT	/routine-exercises/{id}	Update exercise
DELETE	/routine-exercises/{id}	Remove exercise
PUT	/routines/{id}/exercise-order	Reorder exercises

⸻

Workout Sessions

Method	Endpoint	Description
GET	/workout-sessions	Workout history
GET	/workout-sessions/{id}	Workout details
POST	/workout-sessions	Start workout
PUT	/workout-sessions/{id}/complete	Complete workout
PUT	/workout-sessions/{id}/discard	Discard workout

⸻

Workout Exercises

Method	Endpoint	Description
POST	/workout-sessions/{id}/exercises	Add exercise
PUT	/workout-exercises/{id}	Update snapshot
DELETE	/workout-exercises/{id}	Remove exercise

⸻

Workout Sets

Method	Endpoint	Description
POST	/workout-exercises/{id}/sets	Add set
PUT	/workout-sets/{id}	Update set
DELETE	/workout-sets/{id}	Delete set

⸻

8. Standard Response Structure

Successful responses return the requested resource directly.

Example:

{
  "id": "2dfd0b1d-5d98-43ff-8af6-0ef846b2b6d2",
  "name": "Push A"
}

Collections return arrays.

Example:

[
  {
    "id": "...",
    "name": "Push A"
  },
  {
    "id": "...",
    "name": "Pull A"
  }
]

⸻

9. Error Response Format

All errors follow a consistent structure.

{
  "timestamp": "2026-07-20T09:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Routine not found.",
  "path": "/api/v1/routines/123"
}

### Standard Error Mapping

| Exception Type | HTTP Status | Description |
|----------------|------------|-------------|
| Validation failure | 400 Bad Request | Request validation failed. |
| Malformed JSON request | 400 Bad Request | Request body could not be parsed. |
| Resource not found | 404 Not Found | Requested resource does not exist. |
| Business rule violation | 409 Conflict | Request violates a business rule. |
| Unexpected server error | 500 Internal Server Error | Unhandled server-side exception. |

The backend must never expose:

- Java exception names
- Stack traces
- SQL error messages
- Internal package names
- Database implementation details

Only the standard error response format defined above may be returned.

⸻

10. HTTP Status Codes

Status	Usage
200 OK	Successful retrieval/update
201 Created	Resource created
204 No Content	Successful deletion
400 Bad Request	Validation failure
404 Not Found	Resource not found
409 Conflict	Business rule conflict
500 Internal Server Error	Unexpected server error

⸻

11. Validation

The backend validates all incoming requests.

Examples:

* Required fields
* UUID format
* Positive weights
* Positive repetitions
* Valid enum values

Validation failures return:

HTTP 400 Bad Request

using the standard error response format defined in Section 9.

The message field should contain a human-readable validation error.

Example:

```json
{
  "timestamp": "2026-07-20T09:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Routine name must not be blank.",
  "path": "/api/v1/routines"
}
```

⸻

12. API Versioning

All endpoints are prefixed with:

/api/v1

Future breaking changes will use:

/api/v2

⸻

13. Design Guidelines

* Use nouns rather than verbs for resources.
* Use plural resource names.
* Keep URLs lowercase and hyphen-separated.
* Return appropriate HTTP status codes.
* Keep request and response DTOs separate from database entities.
* Never expose JPA entities directly.
* Keep endpoints predictable and consistent.

⸻

14. OpenAPI Documentation

The backend will expose interactive API documentation using SpringDoc OpenAPI.

Development endpoint:

/swagger-ui.html

or

/swagger-ui/index.html

The generated OpenAPI specification becomes the authoritative representation of the implemented REST API.

The project documentation (PRD.md, ARCHITECTURE.md, DATABASE.md, API_SPECIFICATION.md, etc.) remains the architectural source of truth.

Implementation must always follow the approved project documentation. The generated OpenAPI specification reflects the implemented API and should remain synchronized with the documentation.

⸻

15. Definition of Done

An API endpoint is complete when:

* REST conventions are followed.
* Validation is implemented.
* Correct HTTP status codes are returned.
* DTOs are used for requests and responses.
* JPA entities are not exposed directly.
* Endpoint is documented in OpenAPI.
* Automated tests cover success and failure scenarios.