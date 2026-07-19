ADR-0004: Snapshot-Based Workout History

Status

Accepted

⸻

Context

Workout routines are planning templates.

Users may modify routines at any time by:

* Renaming exercises.
* Reordering exercises.
* Changing target sets.
* Changing repetition ranges.
* Changing rest durations.
* Updating exercise notes.

Completed workouts represent historical events.

If completed workouts referenced only the current routine definition, any future modification to the routine would alter previously recorded workout history.

Historical workout records must remain accurate regardless of future template changes.

⸻

Decision

Workout history is stored using historical snapshots.

When a workout begins:

* A WorkoutSession is created.
* Every RoutineExercise is copied into a corresponding WorkoutExercise.
* WorkoutExercise stores the planning information exactly as it existed when the workout started.
* WorkoutSet records store the actual performance for each set.

The resulting data flow is:

Routine
      │
      ▼
RoutineExercise
      │
      │ Copy Snapshot
      ▼
WorkoutExercise
      │
      ▼
WorkoutSet

WorkoutExercise intentionally duplicates selected planning fields from RoutineExercise.

Examples include:

* Exercise name
* Exercise order
* Target sets
* Target repetition range
* Target rest duration
* Exercise notes

These values become part of the historical workout record.

Future modifications to routines or exercises do not alter completed workouts.

⸻

Consequences

Benefits

* Historical workout records remain accurate.
* Routine templates can evolve without affecting past workouts.
* Analytics always operate on the original workout definition.
* Workout history reflects exactly what the user saw during the workout.
* Simplifies reporting and future feature development.

⸻

Trade-offs

* Selected planning data is intentionally duplicated.
* Slightly increased storage requirements.
* Additional copy operation when a workout starts.

The storage overhead is small compared to the benefits of preserving historical accuracy.

⸻

Alternatives Considered

Reference RoutineExercise Directly

Store only foreign keys to RoutineExercise.

Rejected because:

* Editing routines changes historical workouts.
* Historical reports become inaccurate.
* Past workouts no longer represent what actually occurred.

⸻

Version Every Routine

Maintain versioned routine templates.

Rejected because:

* Considerably more complex.
* Increased maintenance overhead.
* More difficult queries.
* Unnecessary for Version 1.

⸻

Implementation Guidelines

* WorkoutExercise is created when a workout starts.
* Snapshot fields must never be updated automatically.
* Editing routines affects only future workouts.
* Completed workout history remains immutable during normal application usage.
* Corrections to completed workouts are permitted only through the dedicated workout edit flow.

⸻

Related Documents

* ARCHITECTURE.md
* DATABASE.md
* API_SPECIFICATION.md
* SYNC.md

⸻

Related ADRs

* ADR-0001: Workout History is the Source of Truth
* ADR-0002: Room is the Android Source of Truth
* ADR-0003: Backend is the System Source of Truth