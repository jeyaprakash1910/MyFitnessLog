import type { AxiosRequestConfig } from 'axios';
import { apiClient } from './client';
import type { WorkoutSessionDetail, WorkoutSessionSummary } from './types';

/** Builds a request config, omitting `signal` entirely when none was given. */
function withSignal(signal: AbortSignal | undefined): AxiosRequestConfig {
  return signal ? { signal } : {};
}

/**
 * Typed wrappers over the read endpoints the web client consumes.
 *
 * Each returns the parsed, decimal-preserved response typed to the mirror in
 * `types.ts`. They throw {@link ApiError} on failure (normalized by the client
 * interceptor). No caching or retry lives here — that is TanStack Query's job,
 * wired in Phase 2. This module is intentionally thin so the query layer has a
 * clean, individually testable seam to mock.
 */

/** `GET /workout-sessions` — history (COMPLETED only, newest first). */
export async function fetchWorkoutHistory(signal?: AbortSignal): Promise<WorkoutSessionSummary[]> {
  const response = await apiClient.get<WorkoutSessionSummary[]>(
    '/workout-sessions',
    withSignal(signal),
  );
  return response.data;
}

/** `GET /workout-sessions/{id}` — the full immutable workout snapshot. */
export async function fetchWorkoutDetail(
  id: string,
  signal?: AbortSignal,
): Promise<WorkoutSessionDetail> {
  const response = await apiClient.get<WorkoutSessionDetail>(
    `/workout-sessions/${encodeURIComponent(id)}`,
    withSignal(signal),
  );
  return response.data;
}

/** `GET /health` — backend liveness. Used by the connectivity smoke check. */
export async function fetchHealth(signal?: AbortSignal): Promise<boolean> {
  const response = await apiClient.get('/health', withSignal(signal));
  return response.status === 200;
}
