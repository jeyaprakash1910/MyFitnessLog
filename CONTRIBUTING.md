# Contributing to MyFitnessLog

Thanks for your interest in contributing. This guide describes how work is expected
to be carried out in this repository — the philosophy behind it, the workflow, and
the standards a change must meet before it is merged.

MyFitnessLog is an offline-first workout tracker built as three components (an Android
app, a Spring Boot backend, and a read-only React web viewer). Before contributing,
read the [README](README.md) for how to build and run each, and skim
[docs/ROADMAP.md](docs/ROADMAP.md) for current status.

## Philosophy

A few principles decide most day-to-day questions:

- **Documentation-first.** The design is written down before it is built, and the
  documentation is kept true to the implementation. If a change alters behaviour that
  a document describes, the document changes in the same change.
- **Architecture-first.** The system has a defined architecture and a set of
  invariants; a change works within them. A change that appears to require breaking one
  is a signal to reconsider the change, not the invariant.
- **KISS and YAGNI.** Prefer the simplest thing that works. Do not add abstraction,
  configuration, or generality for a need that does not yet exist.
- **Small, reviewable units.** Build in small vertical slices, each independently
  reviewable, keeping the app runnable at every step.
- **Runtime verification.** A unit is not done when it compiles — it is done when it
  builds *and its tests pass*. Verify by running, not by inspection.

The authoritative conventions live in
[docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md); this guide points at the parts
that matter for a contribution rather than repeating them.

## Architecture-first design

Before writing code, understand where it belongs. The key references:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — the system across all three
  components.
- [docs/ANDROID_ARCHITECTURE.md](docs/ANDROID_ARCHITECTURE.md) — the Android client.
- [docs/architecture/WORKOUT_LOGGING.md](docs/architecture/WORKOUT_LOGGING.md) — the
  workout-logging domain (state machine, projections, invariants).
- [docs/DATABASE.md](docs/DATABASE.md), [docs/API_SPECIFICATION.md](docs/API_SPECIFICATION.md),
  [docs/SYNC.md](docs/SYNC.md) — the data model, REST contract, and sync strategy.
- [docs/ADR/](docs/ADR/) — the decisions behind the architecture, and *why*.

Rules of thumb that recur across the codebase:

- Business rules live in the domain, never in the UI. If a screen finds itself
  deciding *what* happens (not just rendering and emitting intents), that logic is in
  the wrong layer.
- Read models are read-only. A feature that displays data must not acquire a write
  path.
- On Android, Room is the local source of truth and the UI observes it via Flows; the
  backend is the system of record. The UI never blocks on the network.

An architecturally significant decision — a new contract, a schema/API/sync change, a
new cross-cutting rule — is recorded as an ADR under `docs/ADR/`, following the format
of the existing records. UI polish and local implementation choices do not need one.

## Branching and commits

- **Branch off the default branch** (`main`) for every change; do not commit directly
  to it. Use a short, descriptive branch name (for example, `feat/…` or `fix/…`).
- **Commit messages are imperative and descriptive** — "Add workout session entity",
  not "fix" or "changes". See [CODING_STANDARDS §21](docs/CODING_STANDARDS.md).
- **Group commits by concern.** Prefer a small number of coherent commits over one
  large mixed commit or dozens of fragments.

## Pull requests

Open a pull request against `main`. A good PR:

- does one coherent thing, and says what and why in the description;
- keeps the build green and all tests passing;
- updates any documentation its change affects (see below);
- notes anything intentionally deferred or out of scope.

Every change is reviewed against the PRD, the architecture, the database design, the
API specification, and the coding standards. A change that violates documented
architecture is not merged until it is reconciled — either the change is corrected, or
the architecture is changed deliberately (with an ADR) first.

## Testing expectations

Tests are part of the change, not a follow-up. See
[docs/development/TESTING.md](docs/development/TESTING.md) for the full guide; in short:

- **Every change is verified by running it.** Build and run the relevant test suite
  before considering a unit done.
- **New behaviour is covered by a test**, at the layer that owns the behaviour (domain
  logic as pure unit tests; repository queries against a real database; UI behaviour
  as Compose/Robolectric tests).
- **A rule worth stating is worth a test that fails when it is broken.**
- **Automated tests never touch real data.** No test may read or write the production
  database or a non-disposable backend — this is a hard rule, not a default
  ([CODING_STANDARDS §19b](docs/CODING_STANDARDS.md)).
- **Instrumented Android tests run on an emulator, never a daily-use phone** — the
  device-lifecycle tasks are blocked from doing so because an uninstall deletes real
  training data ([CODING_STANDARDS §20c](docs/CODING_STANDARDS.md)).

## Documentation expectations

- If a change alters behaviour a document describes, update that document in the same
  change.
- If it makes an architecturally significant decision, add an ADR.
- Keep [docs/ROADMAP.md](docs/ROADMAP.md) and
  [docs/TECH_DEBT.md](docs/TECH_DEBT.md) honest — record deferred work as debt rather
  than leaving it implicit.
- Database changes follow the migration discipline in
  [CODING_STANDARDS §20/§20b](docs/CODING_STANDARDS.md): every schema change is a new,
  never-edited migration, with a test proving data survives it.

## Using AI assistance

AI tools may be used to help write code, provided the result meets the same bar as any
other contribution:

- Review generated code as carefully as hand-written code; do not merge what you have
  not read and understood.
- Ensure it follows the project's architecture, layering, and naming conventions, and
  strip unnecessary complexity or speculative abstraction.
- Hold it to the same testing and documentation expectations as everything else.

AI accelerates implementation; it does not replace architectural review or
verification. When a change is AI-assisted, attribute it in the commit trailer as you
would any co-author.

## Definition of done

A change is complete only when it compiles, passes all tests, follows the architecture
and the approved technology stack, adheres to the coding standards, updates the
documentation it affects, and reads consistently with the surrounding code.
