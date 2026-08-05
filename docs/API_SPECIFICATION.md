API Specification

Project: MyFitnessLog
Version: 1.0
Status: Approved — as released in Version 1.0.0 (22 July 2026)
Last Updated: July 22, 2026 (M12 Phase 3 — health `disposable` field)

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

The API enforces an application-level API-key boundary (ADR-0013). When the backend
has `APP_API_KEY` configured, every request must present that value in an `X-API-Key`
header; the API is otherwise default-deny and rejects unauthenticated requests with
`401`. The health endpoint (`/api/v1/health`) is exempt. When `APP_API_KEY` is unset
(local development), the check is disabled and all requests are allowed.

This is app-level, not per-user, authentication: every authenticated request is still
treated as belonging to the single application user.

Per-user authentication will be introduced in a future version without changing
endpoint structures.

⸻

5b. Cross-Origin Requests (CORS)

Browser clients are served from a different origin than the API, so the API
declares an explicit CORS policy. Non-browser clients (Android) are unaffected —
CORS is a browser mechanism.

The policy is deliberately minimal and matches what the read-only web client
needs, nothing more:

Setting	Value
Origins	`app.cors.allowed-origins` (default `http://localhost:5173`, the Vite dev server)
Methods	`GET` only
Headers	`Content-Type`
Credentials	Not allowed
Applies to	`/api/**`

Consequences worth stating: a preflight for any write verb is rejected with 403,
as is a request from an unlisted origin. An empty origin list disables CORS
entirely. Allowing credentials is deliberately off — the API authenticates with an
`X-API-Key` header, not cookies, and enabling credentials would forbid a wildcard
origin later.

⸻

6. Resource Overview

The API exposes the following resources:

Resource	Purpose
Exercises	Exercise library
Exercise Categories	Exercise grouping (id, name, displayOrder)
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

Response fields: `id`, `name`, `displayOrder`.

**Categories are returned in `displayOrder` ascending**, and the value is part of
the contract rather than an implementation detail. Clients that cache categories
locally — Android stores them in Room — can only reproduce the catalogue's
intended grouping if the position travels with the row; without it the client
falls back to alphabetical and the curation is lost (M11 Phase 2, TD-011's
sibling defect D-2).

Soft-deleted categories are excluded, so a withdrawn category simply stops
appearing. Clients are expected to reconcile removals rather than accumulate
stale rows.

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

**History means COMPLETED sessions only.** `GET /workout-sessions` returns
sessions whose status is COMPLETED, newest first (by `startedAt`). IN_PROGRESS
workouts are not history yet, and DISCARDED ones are attempts the user chose to
throw away.

The filter is defined here, on the backend, so every client shares one
definition — a client must not re-implement it. Fetching a discarded or active
session by id via `GET /workout-sessions/{id}` still works; only the list is
filtered. A `?status=` parameter is the intended extension if discarded workouts
ever need to be shown.

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

Health

Method	Endpoint	Description
GET	/health	Liveness, and whether this backend's data is disposable

Response fields: `status`, `disposable`.

```json
{ "status": "UP", "disposable": false }
```

`disposable` tells an automated test whether this instance may be written to. It
is `true` only under the `livetest` Spring profile, which runs on port 8081
against the throwaway `myfitnesslog_livetest` database. Every other profile
reports `false`, and a client must treat an absent field as `false` too.

This is part of the contract rather than a diagnostic, because tests depend on
it to refuse to run. It exists because reachability was previously used as a
proxy for disposability: the Android live sync test probed for any backend on
localhost and wrote to whatever answered, which put 71 test routines into the
system of record (TD-013). A server is the only party that knows what it is, so
it is the party that declares it.

⸻

App Distribution

Method	Endpoint	Description
GET	/app/latest-version	The latest published Android release
GET	/app/apk	Stream that release's signed APK

These serve the Android app itself, not workout data (ADR-0016). The app is
distributed outside any store, so the backend is what tells an installed build
that a newer one exists. Both sit behind the ordinary `X-API-Key` boundary; they
are not on the permit-list.

`GET /app/latest-version` response fields: `versionName`, `releaseNotes`,
`publishedAt`, `sizeBytes`.

```json
{
  "versionName": "1.2.0",
  "releaseNotes": "Editable per-exercise notes during a workout.",
  "publishedAt": "2026-08-01T10:00:00Z",
  "sizeBytes": 13012345
}
```

The response deliberately carries **no `versionCode` and no "update available"
flag**. It reports what the latest release is; whether that is newer than the
caller is the caller's own question, and only the caller knows what it is
running. Answering it here would put the MAJOR.MINOR.PATCH ordering rule in two
codebases at once. `releaseNotes` is Markdown and may be blank; `publishedAt` may
be null; both are presentational, so a client must tolerate their absence and
still compare versions.

`versionName` is taken from the release tag and is always bare
`MAJOR.MINOR.PATCH` (any `v` prefix is stripped). A tag that is not a semantic
version is refused rather than reported, because a wrong version string here
would make the client's comparison silently meaningless.

`GET /app/apk` responds `application/vnd.android.package-archive` with
`Content-Length` set from the release metadata, so a client can render real
download progress. The body is streamed, not buffered.

**Both endpoints return 503 when updates are unconfigured** (`app.update.*`
unset), when the artifact store is unreachable, or when the latest release has no
APK attached. 503 rather than 500 or 404: the request was valid and may succeed
later. Clients are expected to treat it as "cannot tell right now" and stay quiet
rather than surfacing an error, and specifically must not treat it as "you are up
to date".

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
| App update unavailable | 503 Service Unavailable | Updates are unconfigured or the artifact store is unreachable. |
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
503 Service Unavailable	App updates unconfigured or artifact store unreachable

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