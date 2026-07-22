import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type * as ApiModule from '@/api';
import { ApiError } from '@/api';
import { WorkoutDetailPage } from './WorkoutDetailPage';
import { renderWithProviders } from '@/test/renderWithProviders';
import { aWorkoutDetail } from '@/test/fixtures';

const { fetchWorkoutDetail } = vi.hoisted(() => ({ fetchWorkoutDetail: vi.fn() }));

vi.mock('@/api', async (importOriginal) => ({
  ...(await importOriginal<typeof ApiModule>()),
  fetchWorkoutDetail,
}));

const WORKOUT_ID = 'wk000001-0000-4000-8000-000000000001';

function renderDetail() {
  return renderWithProviders(<WorkoutDetailPage />, {
    routePath: '/workouts/:workoutId',
    initialEntries: [`/workouts/${WORKOUT_ID}`],
  });
}

describe('WorkoutDetailPage', () => {
  beforeEach(() => {
    fetchWorkoutDetail.mockReset();
  });

  it('shows a skeleton while loading', () => {
    fetchWorkoutDetail.mockReturnValue(new Promise(() => {}));
    renderDetail();
    expect(screen.getByTestId('history-loading')).toBeInTheDocument();
  });

  it('renders a completed workout reached by direct URL', async () => {
    fetchWorkoutDetail.mockResolvedValue(aWorkoutDetail());
    renderDetail();

    expect(await screen.findByTestId('workout-detail')).toBeInTheDocument();
    expect(screen.getByText('1h 05m')).toBeInTheDocument();
    expect(fetchWorkoutDetail).toHaveBeenCalledWith(WORKOUT_ID, expect.anything());
  });

  it('treats a discarded workout as Not Found', async () => {
    // The detail endpoint is not status-filtered, so it will return this. The
    // web client must not expose what history deliberately hides.
    fetchWorkoutDetail.mockResolvedValue(aWorkoutDetail({ status: 'DISCARDED' }));
    renderDetail();

    expect(await screen.findByTestId('workout-not-found')).toBeInTheDocument();
    expect(screen.queryByTestId('workout-detail')).not.toBeInTheDocument();
  });

  it('treats an in-progress workout as Not Found', async () => {
    fetchWorkoutDetail.mockResolvedValue(aWorkoutDetail({ status: 'IN_PROGRESS' }));
    renderDetail();

    expect(await screen.findByTestId('workout-not-found')).toBeInTheDocument();
  });

  it('treats a 404 from the backend as Not Found', async () => {
    fetchWorkoutDetail.mockRejectedValue(
      new ApiError('client', 'Workout session not found.', { status: 404 }),
    );
    renderDetail();

    expect(await screen.findByTestId('workout-not-found')).toBeInTheDocument();
    expect(screen.queryByTestId('history-error')).not.toBeInTheDocument();
  });

  it('does not reveal that a discarded workout exists', async () => {
    // Both Not Found paths must look identical, or the difference leaks the
    // existence of a workout history is hiding.
    fetchWorkoutDetail.mockResolvedValue(aWorkoutDetail({ status: 'DISCARDED' }));
    const { unmount } = renderDetail();
    const discardedText = (await screen.findByTestId('workout-not-found')).textContent;
    unmount();

    fetchWorkoutDetail.mockRejectedValue(
      new ApiError('client', 'Workout session not found.', { status: 404 }),
    );
    renderDetail();
    const missingText = (await screen.findByTestId('workout-not-found')).textContent;

    expect(discardedText).toBe(missingText);
  });

  it('shows the error state with retry for a server failure', async () => {
    fetchWorkoutDetail.mockRejectedValueOnce(new ApiError('server', 'boom', { status: 500 }));
    renderDetail();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByTestId('workout-not-found')).not.toBeInTheDocument();

    const user = userEvent.setup();
    fetchWorkoutDetail.mockResolvedValue(aWorkoutDetail());
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByTestId('workout-detail')).toBeInTheDocument();
  });

  it('shows a network error distinctly from Not Found', async () => {
    fetchWorkoutDetail.mockRejectedValue(new ApiError('network', 'offline'));
    renderDetail();

    expect(await screen.findByRole('alert')).toHaveTextContent(/backend is running/i);
  });

  it('loads the workout with a single request', async () => {
    fetchWorkoutDetail.mockResolvedValue(aWorkoutDetail());
    renderDetail();

    await screen.findByTestId('workout-detail');
    // The backend assembles the whole snapshot; nothing may fetch per exercise.
    await waitFor(() => expect(fetchWorkoutDetail).toHaveBeenCalledTimes(1));
  });

  it('offers a way back to history', async () => {
    fetchWorkoutDetail.mockResolvedValue(aWorkoutDetail());
    renderDetail();

    await screen.findByTestId('workout-detail');
    expect(screen.getByRole('link', { name: /back to history/i })).toHaveAttribute('href', '/');
  });
});
