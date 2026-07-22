import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api';

/**
 * The app-wide TanStack Query client.
 *
 * Defaults are set here rather than per-query so the whole app shares one
 * server-state policy (MILESTONE_10_PLAN §6). Phase 2 tunes per-query overrides
 * (e.g. `staleTime: Infinity` for immutable completed-workout detail).
 *
 * Retry is deliberately not blind: a 4xx is the backend rejecting the request,
 * and retrying it changes nothing, so only transient failures (network / 5xx)
 * are retried, matching how the Android sync engine classifies failures.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (error instanceof ApiError && (error.kind === 'client' || error.kind === 'unknown')) {
          return false;
        }
        return failureCount < 2;
      },
      staleTime: 30_000,
    },
  },
});
