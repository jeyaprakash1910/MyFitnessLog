ADR-0005: Disable Open Session In View

Status

Accepted

⸻

Context

Spring Boot enables the Open Session In View (OSIV) pattern by default. With OSIV enabled, the Hibernate persistence session remains open for the entire duration of an HTTP request, including view/response rendering.

During Milestone 1 runtime verification, Spring Boot logged the following warning at startup:

spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering.

The MyFitnessLog architecture defines strict layer boundaries (see ARCHITECTURE.md):

* Controllers are thin and contain no business logic.
* Business logic resides in the Service layer.
* Persistence is confined to the Repository layer.

OSIV weakens these boundaries. Because the persistence session stays open beyond the Service layer, lazy-loaded associations can be triggered from the controller or during response serialization. This allows database access to occur outside the Repository/Service layers, contradicting the documented separation of responsibilities.

⸻

Decision

Open Session In View is disabled.

The following configuration is set in application.yml:

spring:
  jpa:
    open-in-view: false

With OSIV disabled, the persistence session is bound to the Service-layer transaction boundary. Any attempt to access lazy-loaded data outside that boundary fails fast rather than silently issuing additional queries during view rendering.

⸻

Consequences

Benefits

* Enforces the documented layered architecture.
* Prevents accidental database access outside the Repository and Service layers.
* Surfaces lazy-loading mistakes early, during development, instead of hiding them.
* Encourages Services to explicitly fetch all data required by the response.
* Removes the startup warning and makes the configuration intentional and documented.

⸻

Trade-offs

* Services must ensure all data needed by the response is loaded within the transaction (for example, using explicit fetching or mapping to DTOs before returning).
* Lazy-loading outside the Service layer will now throw LazyInitializationException.

These trade-offs are acceptable and desirable, because they reinforce the architectural boundaries the project already requires.

⸻

Alternatives Considered

Leave OSIV Enabled (Default)

Rejected because:

* It weakens the documented layer boundaries.
* It permits database access during view rendering.
* It hides lazy-loading issues that should be caught early.

⸻

Implementation Guidelines

* Services must fully prepare the data required by the response before returning.
* Entities must not be relied upon for lazy-loading after leaving the Service layer.
* DTOs remain the boundary between persistence and the API, consistent with CODING_STANDARDS.md.

⸻

Related Documents

* ARCHITECTURE.md
* TECH_STACK.md
* CODING_STANDARDS.md

⸻

Related ADRs

* ADR-0003: Backend is the System Source of Truth
