import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type * as ApiModule from '@/api';
import { routeConfig } from './routeConfig';
import { workoutDetailPath } from './routes';

// The history page fetches on mount. These tests are about routing, not data,
// so the request is stubbed as permanently pending — every route still renders
// its page, just in its loading state.
const { fetchWorkoutHistory, fetchWorkoutDetail } = vi.hoisted(() => ({
  fetchWorkoutHistory: vi.fn(() => new Promise<never>(() => {})),
  fetchWorkoutDetail: vi.fn(() => new Promise<never>(() => {})),
}));

vi.mock('@/api', async (importOriginal) => ({
  ...(await importOriginal<typeof ApiModule>()),
  fetchWorkoutHistory,
  fetchWorkoutDetail,
}));

function renderAt(path: string) {
  const router = createMemoryRouter(routeConfig, { initialEntries: [path] });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

/**
 * Proves the routing wiring resolves — the shell renders, each path reaches its
 * page, and the detail route's parameter is actually delivered to the page.
 */
describe('routing', () => {
  it('renders the history page at the root path', () => {
    renderAt('/');
    expect(screen.getByRole('heading', { name: 'Workout History' })).toBeInTheDocument();
  });

  it('renders the shell on every route', () => {
    renderAt('/');
    expect(screen.getByRole('link', { name: 'MyFitnessLog' })).toBeInTheDocument();
  });

  it('renders the detail page and passes the workout id through', () => {
    const id = '3b1f7683-cb6c-483f-b5da-482f341e19a3';
    renderAt(workoutDetailPath(id));

    // The page renders (its back-link is present) and the route param actually
    // reached it — asserted via the id the page fetched, which is stronger than
    // checking rendered text.
    expect(screen.getByRole('link', { name: /back to history/i })).toBeInTheDocument();
    expect(fetchWorkoutDetail).toHaveBeenCalledWith(id, expect.anything());
  });

  it('falls back to the not-found page for an unknown URL', () => {
    renderAt('/nope');
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});
