import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { WorkoutSessionSummary } from '@/api';
import { WorkoutHistoryList } from './WorkoutHistoryList';
import { renderWithProviders } from '@/test/renderWithProviders';

function summary(overrides: Partial<WorkoutSessionSummary> = {}): WorkoutSessionSummary {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    routineId: '22222222-2222-4222-8222-222222222222',
    status: 'COMPLETED',
    startedAt: '2026-07-21T09:00:00Z',
    endedAt: '2026-07-21T10:05:00Z',
    notes: null,
    ...overrides,
  };
}

describe('WorkoutHistoryList', () => {
  it('renders a row per workout with date and derived duration', () => {
    renderWithProviders(<WorkoutHistoryList workouts={[summary()]} />);

    const row = screen.getByTestId('history-row-11111111-1111-4111-8111-111111111111');
    expect(within(row).getByText('1h 05m')).toBeInTheDocument();
    expect(within(row).getByText(/Routine Workout/)).toBeInTheDocument();
  });

  it('labels a manual workout as such', () => {
    renderWithProviders(<WorkoutHistoryList workouts={[summary({ routineId: null })]} />);
    expect(screen.getByText(/Manual Workout/)).toBeInTheDocument();
  });

  it('preserves the order the backend returned rather than re-sorting', () => {
    // The backend guarantees newest-first; re-sorting here would duplicate that
    // rule in a second place. So the DOM order must equal the input order.
    const workouts = [
      summary({ id: 'aaaaaaaa-1111-4111-8111-111111111111', startedAt: '2026-07-21T09:00:00Z' }),
      summary({ id: 'bbbbbbbb-2222-4222-8222-222222222222', startedAt: '2026-07-19T09:00:00Z' }),
      summary({ id: 'cccccccc-3333-4333-8333-333333333333', startedAt: '2026-07-20T09:00:00Z' }),
    ];
    renderWithProviders(<WorkoutHistoryList workouts={workouts} />);

    const rendered = screen
      .getAllByRole('listitem')
      .map((li) => within(li).getByRole('link').getAttribute('href'));
    expect(rendered).toEqual([
      '/workouts/aaaaaaaa-1111-4111-8111-111111111111',
      '/workouts/bbbbbbbb-2222-4222-8222-222222222222',
      '/workouts/cccccccc-3333-4333-8333-333333333333',
    ]);
  });

  it('shows a trimmed notes preview when notes have content', () => {
    renderWithProviders(<WorkoutHistoryList workouts={[summary({ notes: '  felt strong  ' })]} />);
    expect(screen.getByText('felt strong')).toBeInTheDocument();
  });

  it('omits the notes preview when notes are blank', () => {
    renderWithProviders(<WorkoutHistoryList workouts={[summary({ notes: '   ' })]} />);
    const row = screen.getByRole('link');
    // Only the date/duration line and the type line — no empty third line.
    expect(within(row).getAllByText(/\S/, { selector: 'p' })).toHaveLength(1);
  });

  it('navigates to the workout detail route when a row is activated', async () => {
    const user = userEvent.setup();
    renderWithProviders(<WorkoutHistoryList workouts={[summary()]} />);

    await user.click(screen.getByRole('link'));

    expect(screen.getByTestId('detail-route')).toBeInTheDocument();
  });

  it('exposes each row as a link so keyboard and new-tab behaviour work', () => {
    renderWithProviders(<WorkoutHistoryList workouts={[summary()]} />);
    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      '/workouts/11111111-1111-4111-8111-111111111111',
    );
  });
});
