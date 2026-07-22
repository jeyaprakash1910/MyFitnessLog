import type { WorkoutExerciseDetail } from '@/api';
import { sanitizeNotes } from '@/format/history';
import { WorkoutSetsTable } from './WorkoutSetsTable';

/**
 * One snapshotted exercise and its sets, mirroring Android's `ExerciseCard`:
 * a `position. name` heading, optional notes, then the sets — or an explicit
 * "No sets recorded." when the exercise was added but never logged.
 *
 * `position` is `exerciseOrder + 1`, exactly as Android derives it, so the
 * numbering a user sees matches between the two clients.
 *
 * Order is taken from the payload, not re-sorted: the backend assembles
 * exercises by `exerciseOrder` and sets by `setNumber`, and the snapshot's
 * captured order is the historical record (ADR-0004).
 */
export function WorkoutExerciseCard({ exercise }: { exercise: WorkoutExerciseDetail }) {
  const notes = sanitizeNotes(exercise.notes);

  return (
    <li
      className="rounded-lg border border-slate-200 bg-white p-4"
      data-testid={`exercise-${exercise.id}`}
    >
      <h2 className="font-semibold text-slate-900">
        {exercise.exerciseOrder + 1}. {exercise.exerciseName}
      </h2>
      {notes !== null && <p className="mt-1 text-sm text-slate-500">{notes}</p>}
      {exercise.sets.length === 0 ? (
        <p className="mt-2 text-sm text-slate-500">No sets recorded.</p>
      ) : (
        <WorkoutSetsTable sets={exercise.sets} label={exercise.exerciseName} />
      )}
    </li>
  );
}
