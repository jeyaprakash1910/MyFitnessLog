import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { WorkoutDetailView } from './WorkoutDetailView';
import { WorkoutHistoryList } from './WorkoutHistoryList';
import { EmptyState, ErrorState, LoadingList, NotFoundState } from './StateViews';
import { ApiError, type WorkoutSessionSummary } from '@/api';
import { anExercise, aSet, aWorkoutDetail } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

/**
 * Accessibility guarantees, asserted so they cannot silently regress.
 *
 * These are the properties a manual audit checked in Phase 4. Writing them down
 * as tests is what stops the next styling change from quietly removing a focus
 * ring or flattening the heading hierarchy — both of which had already happened
 * once before this phase.
 */

function summary(overrides: Partial<WorkoutSessionSummary> = {}): WorkoutSessionSummary {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    routineId: null,
    status: 'COMPLETED',
    startedAt: '2026-07-21T09:00:00Z',
    endedAt: '2026-07-21T10:05:00Z',
    notes: null,
    ...overrides,
  };
}

describe('heading hierarchy', () => {
  it('does not skip a level on the detail page', () => {
    // h1 (workout date) → h2 (exercise). Jumping straight to h3 breaks
    // screen-reader document outline navigation.
    render(
      <WorkoutDetailView workout={aWorkoutDetail({ exercises: [anExercise({ sets: [] })] })} />,
    );

    const levels = screen
      .getAllByRole('heading')
      .map((h) => Number(h.tagName.substring(1)))
      .sort((a, b) => a - b);

    expect(levels[0]).toBe(1);
    for (let i = 1; i < levels.length; i++) {
      expect(levels[i]! - levels[i - 1]!).toBeLessThanOrEqual(1);
    }
  });

  it('has exactly one h1 on the detail page', () => {
    render(<WorkoutDetailView workout={aWorkoutDetail()} />);
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });
});

describe('keyboard navigation', () => {
  it('reaches a history row by Tab and opens it with Enter', async () => {
    const user = userEvent.setup();
    renderWithProviders(<WorkoutHistoryList workouts={[summary()]} />);

    await user.tab();
    expect(screen.getByRole('link')).toHaveFocus();

    await user.keyboard('{Enter}');
    expect(screen.getByTestId('detail-route')).toBeInTheDocument();
  });

  it('makes the scrollable sets table focusable so clipped content is reachable', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({ exercises: [anExercise({ sets: [aSet()] })] })}
      />,
    );

    const region = screen.getByRole('region', { name: /sets for/i });
    expect(region).toHaveAttribute('tabindex', '0');
  });

  it('gives the retry button an accessible name', () => {
    render(<ErrorState error={new ApiError('server', 'x')} onRetry={() => {}} />);
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});

describe('table semantics', () => {
  it('marks up sets with column and row headers', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({ exercises: [anExercise({ sets: [aSet()] })] })}
      />,
    );

    const table = screen.getByRole('table');
    const columnHeaders = within(table).getAllByRole('columnheader');
    expect(columnHeaders.map((h) => h.textContent)).toEqual(['Set', 'Weight × Reps', 'Type']);
    // The set number is a row header, so a screen reader can announce which set
    // a value belongs to when navigating cell by cell.
    expect(within(table).getAllByRole('rowheader').length).toBeGreaterThan(0);
  });

  it('names the table for screen readers', () => {
    render(
      <WorkoutDetailView
        workout={aWorkoutDetail({
          exercises: [anExercise({ exerciseName: 'Barbell Back Squat', sets: [aSet()] })],
        })}
      />,
    );
    expect(screen.getByRole('table', { name: /Barbell Back Squat/ })).toBeInTheDocument();
  });
});

describe('status announcements', () => {
  it('announces loading politely', () => {
    render(<LoadingList />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading workouts');
  });

  it('announces errors assertively', () => {
    render(<ErrorState error={new ApiError('network', 'x')} onRetry={() => {}} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('hides decorative skeleton bars from assistive technology', () => {
    const { container } = render(<LoadingList />);
    expect(container.querySelector('ul')).toHaveAttribute('aria-hidden', 'true');
  });
});

describe('recovery affordances', () => {
  it('offers a link back to history from the not-found state', () => {
    renderWithProviders(<NotFoundState />, {
      routePath: '/workouts/:workoutId',
      initialEntries: ['/workouts/x'],
    });
    expect(screen.getByRole('link', { name: /back to history/i })).toBeInTheDocument();
  });

  it('explains the empty state rather than showing a bare message', () => {
    render(<EmptyState />);
    expect(screen.getByTestId('history-empty')).toHaveTextContent(/phone syncs/i);
  });
});
