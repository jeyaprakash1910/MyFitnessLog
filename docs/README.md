# Documentation Index

This is the entry point for browsing MyFitnessLog's documentation. Documents are
grouped by category below, each with a one-line description. For a project overview and
build instructions, start with the [root README](../README.md).

## Product

| Document | Description |
|---|---|
| [PRD.md](PRD.md) | Product requirements — goals, scope, and the problem being solved. |
| [ROADMAP.md](ROADMAP.md) | Current status and milestones — the quick "what's done / next" picture. |

## Architecture

| Document | Description |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System-wide architecture across all three components. |
| [ANDROID_ARCHITECTURE.md](ANDROID_ARCHITECTURE.md) | The Android client's architecture, conventions, and rationale. |
| [ANDROID_FLOW.md](ANDROID_FLOW.md) | Screen-by-screen navigation flow and UI responsibilities. |
| [architecture/WORKOUT_LOGGING.md](architecture/WORKOUT_LOGGING.md) | The workout-logging domain — session lifecycle, set state machine, projections, invariants. |
| [SYNC.md](SYNC.md) | The offline-first, one-way synchronization strategy. |

## API

| Document | Description |
|---|---|
| [API_SPECIFICATION.md](API_SPECIFICATION.md) | The REST API contract consumed by the Android and web clients. |

## Database

| Document | Description |
|---|---|
| [DATABASE.md](DATABASE.md) | The canonical relational data model and migration discipline. |

## Development

| Document | Description |
|---|---|
| [CODING_STANDARDS.md](CODING_STANDARDS.md) | Authoritative coding, testing, migration, and Git conventions. |
| [TECH_STACK.md](TECH_STACK.md) | The official technology choices for each component. |
| [development/TESTING.md](development/TESTING.md) | Testing guide — layers, how to run each suite, disposable-backend and device-guard rules. |
| [TECH_DEBT.md](TECH_DEBT.md) | The accepted technical-debt register. |
| [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) | The authoritative procedure for cutting a release. |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | How to contribute — philosophy, workflow, and expectations. |

## Architecture Decision Records

| ADR | Decision |
|---|---|
| [ADR-0001](ADR/0001-history-is-source-of-truth.md) | Workout history is the source of truth. |
| [ADR-0002](ADR/0002-room-is-android-source-of-truth.md) | Room is the Android source of truth. |
| [ADR-0003](ADR/0003-backend-is-system-source-of-truth.md) | The backend is the system source of truth. |
| [ADR-0004](ADR/0004-snapshot-based-workout-history.md) | Snapshot-based workout history. |
| [ADR-0005](ADR/0005-disable-open-session-in-view.md) | Disable open session in view. |
| [ADR-0006](ADR/0006-timestamp-auditing-via-jpa-auditing.md) | Timestamp auditing via Spring Data JPA auditing. |
| [ADR-0007](ADR/0007-workout-set-deletion-tombstones.md) | Workout set deletions propagate via tombstones. |
| [ADR-0008](ADR/0008-event-driven-set-logging.md) | Event-driven set logging: transient intent, persisted on completion. |
| [ADR-0009](ADR/0009-rest-as-a-domain-event.md) | Rest is a domain event consumed by a session-scoped timer. |
| [ADR-0010](ADR/0010-session-execution-vs-routine-templates.md) | Session-scoped execution never mutates routine templates. |
| [ADR-0011](ADR/0011-read-only-workout-projections.md) | Workout state is surfaced through read-only projections. |
| [ADR-0012](ADR/0012-snake-case-schema-identifiers.md) | Schema identifiers are unquoted snake_case. |
| [ADR-0013](ADR/0013-api-key-auth-boundary.md) | A shared API key is the V1 authentication boundary. |
| [ADR-0014](ADR/0014-dual-pooler-datasource.md) | Runtime and Flyway use separate Supabase poolers. |
| [ADR-0015](ADR/0015-docker-deployment-on-render.md) | The backend deploys to Render as a Docker image. |
| [ADR-0016](ADR/0016-in-app-update-delivery.md) | The backend delivers app updates from GitHub Releases. |
| [ADR-0017](ADR/0017-backend-convergence-in-three-stages.md) | The backend becomes readable, in three stages. |

## Releases

| Document | Description |
|---|---|
| [../CHANGELOG.md](../CHANGELOG.md) | Notable changes per version (Keep a Changelog). |
| [V1_RELEASE_NOTES.md](V1_RELEASE_NOTES.md) | Version 1.0.0 release notes — capabilities and scope. |

## Repository root

| Document | Description |
|---|---|
| [../README.md](../README.md) | Project overview, repository layout, and build/run instructions. |
| [../LICENSE](../LICENSE) | The project license (MIT). |
| [../SECURITY.md](../SECURITY.md) | Security policy — scope, deployment assumptions, and reporting. |
| [../CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) | Community code of conduct (Contributor Covenant). |
