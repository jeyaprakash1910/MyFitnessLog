import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';

/**
 * Renders a component inside the providers it needs: a router (for `Link`) and
 * a fresh QueryClient.
 *
 * The client is created per call and has retries **off**. Sharing one across
 * tests leaks cache between them, and retries would make an error-path test wait
 * through backoff before asserting.
 */
export function renderWithProviders(
  ui: ReactElement,
  {
    initialEntries = ['/'],
    routePath = '/',
  }: { initialEntries?: string[]; routePath?: string } = {},
): RenderResult & { router: ReturnType<typeof createMemoryRouter> } {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
    },
  });

  // `routePath` lets a page under test be mounted at a parameterised path (the
  // detail page needs :workoutId in scope for useParams). The extra routes are
  // navigation targets so links can be followed without pulling in real pages.
  const routes = [
    { path: routePath, element: ui },
    ...(routePath === '/workouts/:workoutId'
      ? [{ path: '/', element: <div data-testid="history-route" /> }]
      : [{ path: '/workouts/:workoutId', element: <div data-testid="detail-route" /> }]),
  ];

  const router = createMemoryRouter(routes, { initialEntries });

  const result = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return { ...result, router };
}
