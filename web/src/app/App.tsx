import { QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { queryClient } from './queryClient';
import { routeConfig } from './routeConfig';

const router = createBrowserRouter(routeConfig);

/** Application root: provides server-state and routing to the whole tree. */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
