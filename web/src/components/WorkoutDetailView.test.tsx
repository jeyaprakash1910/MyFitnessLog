import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { WorkoutDetailView } from './WorkoutDetailView';
import { anExercise, aSet, aWorkoutDetail, dec } from '@/test/fixtures';

describe('WorkoutDetailView', () => {
  it('renders workout metadata', () => {
    render(<WorkoutDetailView workout={aWorkoutDetail()} />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/2026/);
    expect(screen.getByText('1h 05m')).toBeInTheDocument();
    expect(screen.getByText(/Routine Workout · ended/)).toBeInTheDocument();
  });

  it('labels a manual workout and shows its notes', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({ routineId: null, notes: '  tough session  ' })}
      />,
    );

    expect(screen.getByText(/Manual Workout/)).toBeInTheDocument();
    expect(screen.getByText('tough session')).toBeInTheDocument();
  });

  it('numbers exercises from one, matching Android', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({
          exercises: [
            anExercise({ id: 'a', exerciseOrder: 0, exerciseName: 'Squat', sets: [] }),
            anExercise({ id: 'b', exerciseOrder: 1, exerciseName: 'Bench', sets: [] }),
          ],
        })}
      />,
    );

    expect(screen.getByRole('heading', { name: '1. Squat' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '2. Bench' })).toBeInTheDocument();
  });

  it('preserves the snapshot order rather than re-sorting', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({
          exercises: [
            anExercise({ id: 'b', exerciseOrder: 1, exerciseName: 'Bench', sets: [] }),
            anExercise({ id: 'a', exerciseOrder: 0, exerciseName: 'Squat', sets: [] }),
          ],
        })}
      />,
    );

    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(headings).toEqual(['2. Bench', '1. Squat']);
  });

  it('renders sets as a table with weight, reps and category', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({
          exercises: [
            anExercise({
              sets: [
                aSet({ id: 's1', setNumber: 1, weight: dec('60.00'), setCategory: 'WARMUP' }),
                aSet({ id: 's2', setNumber: 2, weight: dec('102.50'), repetitions: 5 }),
              ],
            }),
          ],
        })}
      />,
    );

    const table = screen.getByRole('table');
    expect(within(table).getByText('60 × 8')).toBeInTheDocument();
    expect(within(table).getByText('102.5 × 5')).toBeInTheDocument();
    expect(within(table).getByText('Warm-up')).toBeInTheDocument();
  });

  it('preserves decimal scale from the payload without numeric conversion', () => {
    // The end-to-end point of DecimalString: 102.50 must not surface as 102.5
    // by accident of parsing, nor as "102.50" — Android trims to 102.5.
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({
          exercises: [anExercise({ sets: [aSet({ weight: dec('102.50'), repetitions: 5 })] })],
        })}
      />,
    );
    expect(screen.getByText('102.5 × 5')).toBeInTheDocument();
  });

  it('renders a bodyweight set as 0 rather than 0.00', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({
          exercises: [anExercise({ sets: [aSet({ weight: dec('0.00'), repetitions: 12 })] })],
        })}
      />,
    );
    expect(screen.getByText('0 × 12')).toBeInTheDocument();
  });

  it('shows RPE and RIR only when present', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({
          exercises: [
            anExercise({
              sets: [
                aSet({ id: 's1', rpe: dec('8.5'), rir: null }),
                aSet({ id: 's2', setNumber: 2, rpe: null, rir: dec('2') }),
                aSet({ id: 's3', setNumber: 3, rpe: null, rir: null }),
              ],
            }),
          ],
        })}
      />,
    );

    expect(screen.getByText(/RPE 8\.5/)).toBeInTheDocument();
    expect(screen.getByText(/RIR 2/)).toBeInTheDocument();
    // The set with neither shows the category alone — no stray separators.
    const bare = screen.getByTestId('set-row-s3');
    expect(bare).toHaveTextContent('Working');
    expect(bare.textContent).not.toMatch(/RPE|RIR|·/);
  });

  it('states explicitly when an exercise has no sets', () => {
    render(
      <WorkoutDetailView workout={aWorkoutDetail({ exercises: [anExercise({ sets: [] })] })} />,
    );
    expect(screen.getByText('No sets recorded.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('handles a workout with no exercises', () => {
    render(<WorkoutDetailView workout={aWorkoutDetail({ exercises: [] })} />);
    expect(screen.getByText('No exercises recorded.')).toBeInTheDocument();
  });

  it('exposes no controls that could mutate the workout', () => {
    // The web client is read-only (SYNC.md §6). A button or input appearing here
    // would be a scope violation, not a styling detail.
    render(<WorkoutDetailView workout={aWorkoutDetail()} />);
    expect(screen.queryAllByRole('button')).toHaveLength(0);
    expect(screen.queryAllByRole('textbox')).toHaveLength(0);
  });
});
