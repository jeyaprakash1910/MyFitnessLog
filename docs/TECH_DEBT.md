# Technical Debt Register

Project: MyFitnessLog
Version: 1.0
Last Updated: July 20, 2026

This document records known, accepted technical debt: deliberate limitations that are not defects in the current milestone but must be addressed in a later milestone. Each item states the observation, why it is currently acceptable, the recommended future implementation, the documentation that must change first, and when it is scheduled.

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
