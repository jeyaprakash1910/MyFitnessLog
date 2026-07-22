import type { RouteObject } from 'react-router-dom';
import { AppLayout } from './AppLayout';
import { routes } from './routes';
import { HistoryPage } from '@/pages/HistoryPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { WorkoutDetailPage } from '@/pages/WorkoutDetailPage';

/**
 * The route tree, kept separate from {@link App} so tests can mount it with
 * `createMemoryRouter` instead of a browser router. Every page renders inside
 * {@link AppLayout}; unmatched URLs fall through to {@link NotFoundPage}.
 */
export const routeConfig: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { path: routes.history, element: <HistoryPage /> },
      { path: routes.workoutDetail, element: <WorkoutDetailPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
