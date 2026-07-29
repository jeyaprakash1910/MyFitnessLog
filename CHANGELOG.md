# Changelog

All notable changes to MyFitnessLog are recorded here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

A redesign of the active workout-logging experience — the screen used while training.
The change is client-side (Android UI and domain) and composes the existing persistence
and synchronization, adding no database, API, or sync-protocol change.

### Added

- Inline set logging: edit weight and reps directly in the set table, with previous
  performance shown per set and one-tap completion.
- RPE capture through a bottom sheet that records effort and completes the set in one
  action.
- A per-exercise rest duration, editable during the workout, feeding a single rest
  countdown that continues while navigating between screens.
- Session-local exercise management: reorder and remove exercises for today's workout
  without changing the underlying routine.
- A persistent workout indicator on other screens showing the routine, elapsed time,
  and current exercise, with resume and a confirmed discard.

### Changed

- Set logging is now event-driven: a set becomes part of history only when it is
  completed, so planned and partially-entered rows never enter history, sync, or
  analytics. Editing a completed set changes its values without re-triggering
  completion or rest.
- Undoing a completed set restores it in place as an editable row rather than leaving a
  record behind.

### Documentation

- Added a public [workout-logging architecture reference](docs/architecture/WORKOUT_LOGGING.md)
  and architecture decision records
  [ADR-0008](docs/ADR/0008-event-driven-set-logging.md) through
  [ADR-0011](docs/ADR/0011-read-only-workout-projections.md).

## [1.0.0] — 2026-07-22

First release: a signed Android app and a read-only web viewer, both talking to a
backend on a private network. See the full
[release notes](docs/V1_RELEASE_NOTES.md).

### Added

- **Routines.** Create, rename, duplicate, and delete routines; add exercises with
  target sets, a rep range, and rest time; reorder and remove them.
- **Workouts.** Start from scratch or from a routine, add exercises mid-session, log
  sets with weight, reps, RPE/RIR, and a set category, run a rest timer, then complete
  or discard. Elapsed time is derived from the start timestamp, so it survives process
  death.
- **Exercise library.** Exercises across many categories, downloaded from the backend
  and cached locally, with search and category filtering.
- **History.** Every completed workout is an immutable snapshot of the exercises and
  targets as they were on the day; later routine or catalogue edits never rewrite it.
- **Synchronization.** One-way, Android → backend, in the background. Everything works
  offline; writes queue locally and upload when a backend is reachable, with idempotent
  UUID-keyed replay.
- **Web viewer.** Read-only history list and full workout detail, responsive from
  mobile to desktop, with decimal weights preserved exactly from the database.

### Notes

- Version 1 is a local production release for personal or trusted-group use on a
  private backend. There is no authentication — the network boundary is the security
  boundary. Multi-user support is a future version.
- Synchronization is one-way: the backend is the durable copy but cannot repopulate a
  device, so a phone that loses its database does not get its history back.

[Unreleased]: https://github.com/jeyaprakash1910/MyFitnessLog/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/jeyaprakash1910/MyFitnessLog/releases/tag/v1.0.0
