-- The routine's name as it was when the workout started.
--
-- Snapshotted rather than joined, for the same reason workout_exercise already
-- copies exercise_name: a workout records what happened, and renaming a routine
-- afterwards must not retroactively relabel every session that used it. Joining
-- routine.name at read time would do exactly that (ADR-0004).
--
-- Nullable, because two cases legitimately have no name: a manual workout, which
-- has no routine at all, and any session recorded before this column existed.
-- Clients fall back to a generic label rather than inventing one.
ALTER TABLE workout_session
    ADD COLUMN routine_name VARCHAR(100);

COMMENT ON COLUMN workout_session.routine_name IS
    'Routine name captured at workout start; null for manual workouts and pre-V8 rows.';
