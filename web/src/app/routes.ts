/**
 * Centralized route paths.
 *
 * Defining them once (rather than as string literals scattered across `<Link>`s
 * and `useNavigate` calls) means the detail-route parameter name and the URL
 * shape have a single source of truth. Phase 2/3 add the real pages behind these
 * same paths.
 */
export const routes = {
  history: '/',
  workoutDetail: '/workouts/:workoutId',
} as const;

/** Builds the detail path for a given workout id. */
export function workoutDetailPath(workoutId: string): string {
  return `/workouts/${encodeURIComponent(workoutId)}`;
}
