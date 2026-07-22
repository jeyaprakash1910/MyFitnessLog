import type { WorkoutSessionDetail } from '@/api';
import {
  formatCompletedDuration,
  formatTimeOfDay,
  formatWorkoutDate,
  sanitizeNotes,
  workoutTypeLabel,
} from '@/format/history';
import { WorkoutExerciseCard } from './WorkoutExerciseCard';

/**
 * The workout snapshot: metadata, then one card per exercise.
 *
 * Mirrors Android's `WorkoutDetailContent` layout —
 *
 * ```
 * [date]                          [duration]
 * [type label] · ended [time]
 * [notes]
 * ```
 *
 * — with the completion time added (Phase 3 requires it; Android shows only date
 * and duration on this screen). The type/time line is deliberately identical in
 * shape to the history card, so a user moving between the two sees the same
 * information in the same place.
 *
 * A pure function of the fetched workout: no fetching, so it renders from a
 * fixture in tests, and there is no way for an N+1 to appear at this level.
 */
export function WorkoutDetailView({ workout }: { workout: WorkoutSessionDetail }) {
  const notes = sanitizeNotes(workout.notes);

  return (
    <article data-testid="workout-detail">
      <header>
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h1 className="text-xl font-semibold text-slate-900 sm:text-2xl">
            {formatWorkoutDate(workout.startedAt)}
          </h1>
          <span className="text-xl font-semibold tabular-nums text-slate-900 sm:text-2xl">
            {formatCompletedDuration(workout.startedAt, workout.endedAt)}
          </span>
        </div>
        <p className="mt-1 text-sm text-slate-600">
          {workoutTypeLabel(workout.routineId)}
          {workout.endedAt !== null && ` · ended ${formatTimeOfDay(workout.endedAt)}`}
        </p>
        {notes !== null && (
          <p className="mt-3 whitespace-pre-line rounded-md bg-slate-100 p-3 text-sm text-slate-700">
            {notes}
          </p>
        )}
      </header>

      {workout.exercises.length === 0 ? (
        <p className="mt-6 text-sm text-slate-500">No exercises recorded.</p>
      ) : (
        <ul className="mt-6 space-y-3">
          {workout.exercises.map((exercise) => (
            <WorkoutExerciseCard key={exercise.id} exercise={exercise} />
          ))}
        </ul>
      )}
    </article>
  );
}
