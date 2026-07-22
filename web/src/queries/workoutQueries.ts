import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import {
  fetchWorkoutDetail,
  fetchWorkoutHistory,
  type WorkoutSessionDetail,
  type WorkoutSessionSummary,
} from '@/api';

/**
 * Server-state access for workouts.
 *
 * Query keys are hierarchical so a future manual refresh can invalidate the
 * whole `workouts` subtree, or just the list, without discarding cached detail
 * (MILESTONE_10_PLAN §6).
 */
export const workoutKeys = {
  all: ['workouts'] as const,
  /**
   * The history list. Takes no arguments today; when pagination arrives it
   * becomes `history(params)` and the params join the key, so cached pages stay
   * distinct. Callers already go through this function, so that change does not
   * touch them.
   */
  history: () => [...workoutKeys.all, 'history'] as const,
  detail: (id: string) => [...workoutKeys.all, 'detail', id] as const,
};

/**
 * Fetches workout history — COMPLETED sessions, newest first.
 *
 * Both of those properties are the **backend's** guarantees
 * (API_SPECIFICATION §7), deliberately not re-applied here: duplicating the
 * filter or the sort in a second place is exactly what TD-008 was about.
 *
 * Retry and staleness come from the shared query client defaults (30 s stale,
 * no retry on 4xx), so behaviour matches the rest of the app rather than being
 * configured per call site.
 */
export function useWorkoutHistory(): UseQueryResult<WorkoutSessionSummary[], Error> {
  return useQuery({
    queryKey: workoutKeys.history(),
    queryFn: ({ signal }) => fetchWorkoutHistory(signal),
  });
}

/**
 * Fetches one workout's full snapshot, or `null` when it is not part of history.
 *
 * ## Why `null` rather than the raw response
 *
 * `GET /workout-sessions/{id}` is **not** filtered by status — it will happily
 * return an IN_PROGRESS or DISCARDED session. The list endpoint, by contract, is
 * COMPLETED-only. Rendering a discarded workout that the history list hides
 * would recreate exactly the two-definitions-of-history problem TD-008 removed,
 * just via a deep link instead of a client-side filter.
 *
 * So the rule lives here, once, in the query layer: anything that is not
 * COMPLETED resolves to `null`, and the page renders Not Found. Android reaches
 * the same conclusion independently — its `WorkoutDetailUiState.NotFound` is
 * documented as covering "discarded, in-progress, or deleted".
 *
 * ## Caching
 *
 * `staleTime: Infinity` is not an optimization guess: a completed workout is
 * immutable and the **backend enforces** that (mutations to a terminal session
 * are rejected with 409). It genuinely cannot change once fetched, so this is
 * the one query in the app that never needs revalidating.
 */
export function useWorkoutDetail(
  workoutId: string,
): UseQueryResult<WorkoutSessionDetail | null, Error> {
  return useQuery({
    queryKey: workoutKeys.detail(workoutId),
    queryFn: ({ signal }) => fetchWorkoutDetail(workoutId, signal),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    select: (detail) => (detail.status === 'COMPLETED' ? detail : null),
  });
}
