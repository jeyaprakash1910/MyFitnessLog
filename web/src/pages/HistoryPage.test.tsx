import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type * as ApiModule from '@/api';
import { ApiError, type WorkoutSessionSummary } from '@/api';
import { HistoryPage } from './HistoryPage';
import { renderWithProviders } from '@/test/renderWithProviders';

// Mock only the network call. Everything else — the query hook, formatting,
// components — runs for real, so these tests exercise the actual wiring rather
// than a reimplementation of it.
const { fetchWorkoutHistory } = vi.hoisted(() => ({ fetchWorkoutHistory: vi.fn() }));

vi.mock('@/api', async (importOriginal) => ({
  ...(await importOriginal<typeof ApiModule>()),
  fetchWorkoutHistory,
}));

const workout: WorkoutSessionSummary = {
  id: '11111111-1111-4111-8111-111111111111',
  routineId: '22222222-2222-4222-8222-222222222222',
  status: 'COMPLETED',
  startedAt: '2026-07-21T09:00:00Z',
  endedAt: '2026-07-21T10:05:00Z',
  notes: null,
};

describe('HistoryPage', () => {
  beforeEach(() => {
    fetchWorkoutHistory.mockReset();
  });

  it('shows a skeleton while the first load is in flight', () => {
    fetchWorkoutHistory.mockReturnValue(new Promise(() => {}));
    renderWithProviders(<HistoryPage />);

    expect(screen.getByTestId('history-loading')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Loading workouts');
  });

  it('renders the list once loaded', async () => {
    fetchWorkoutHistory.mockResolvedValue([workout]);
    renderWithProviders(<HistoryPage />);

    expect(await screen.findByTestId('history-list')).toBeInTheDocument();
    expect(screen.getByText('1h 05m')).toBeInTheDocument();
  });

  it('explains that data arrives via phone sync when there is nothing yet', async () => {
    fetchWorkoutHistory.mockResolvedValue([]);
    renderWithProviders(<HistoryPage />);

    const empty = await screen.findByTestId('history-empty');
    expect(empty).toHaveTextContent(/after your phone syncs/i);
  });

  it('shows an actionable message when the backend is unreachable', async () => {
    fetchWorkoutHistory.mockRejectedValue(
      new ApiError('network', 'Could not reach the server. Check your connection.'),
    );
    renderWithProviders(<HistoryPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/backend is running/i);
  });

  it('surfaces the backend message for a 4xx', async () => {
    fetchWorkoutHistory.mockRejectedValue(
      new ApiError('client', 'Workout session not found.', { status: 404 }),
    );
    renderWithProviders(<HistoryPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Workout session not found.');
  });

  it('refetches when the user retries after a failure', async () => {
    const user = userEvent.setup();
    fetchWorkoutHistory.mockRejectedValueOnce(new ApiError('server', 'boom', { status: 500 }));
    renderWithProviders(<HistoryPage />);

    await screen.findByRole('alert');
    fetchWorkoutHistory.mockResolvedValue([workout]);
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByTestId('history-list')).toBeInTheDocument();
  });

  it('fetches once for a render, not once per row', async () => {
    fetchWorkoutHistory.mockResolvedValue([
      workout,
      { ...workout, id: '33333333-3333-4333-8333-333333333333' },
    ]);
    renderWithProviders(<HistoryPage />);

    await screen.findByTestId('history-list');
    // Guards against an N+1 creeping in: the list endpoint is the only call.
    await waitFor(() => expect(fetchWorkoutHistory).toHaveBeenCalledTimes(1));
  });

  it('does not render a status badge, since every row is COMPLETED', async () => {
    fetchWorkoutHistory.mockResolvedValue([workout]);
    renderWithProviders(<HistoryPage />);

    await screen.findByTestId('history-list');
    expect(screen.queryByText(/completed/i)).not.toBeInTheDocument();
  });
});
