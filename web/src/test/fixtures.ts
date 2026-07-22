import type { DecimalString, WorkoutExerciseDetail, WorkoutSessionDetail, WorkoutSet } from '@/api';

/** Casts a literal to a DecimalString, as `parseJsonPreservingDecimals` would produce. */
export const dec = (value: string) => value as DecimalString;

export function aSet(overrides: Partial<WorkoutSet> = {}): WorkoutSet {
  return {
    id: 'set00001-0000-4000-8000-000000000001',
    workoutExerciseId: 'ex000001-0000-4000-8000-000000000001',
    setNumber: 1,
    weight: dec('80.00'),
    repetitions: 8,
    setCategory: 'WORKING',
    startedAt: '2026-07-21T09:05:00Z',
    finishedAt: '2026-07-21T09:06:00Z',
    rpe: null,
    rir: null,
    isCompleted: true,
    ...overrides,
  };
}

export function anExercise(overrides: Partial<WorkoutExerciseDetail> = {}): WorkoutExerciseDetail {
  return {
    id: 'ex000001-0000-4000-8000-000000000001',
    exerciseId: 'cat00001-0000-4000-8000-000000000001',
    exerciseName: 'Barbell Back Squat',
    exerciseOrder: 0,
    targetSets: 3,
    minTargetReps: 8,
    maxTargetReps: 12,
    targetRestSeconds: 90,
    notes: null,
    sets: [aSet()],
    ...overrides,
  };
}

export function aWorkoutDetail(
  overrides: Partial<WorkoutSessionDetail> = {},
): WorkoutSessionDetail {
  return {
    id: 'wk000001-0000-4000-8000-000000000001',
    routineId: 'rt000001-0000-4000-8000-000000000001',
    status: 'COMPLETED',
    startedAt: '2026-07-21T09:00:00Z',
    endedAt: '2026-07-21T10:05:00Z',
    notes: null,
    exercises: [anExercise()],
    ...overrides,
  };
}
