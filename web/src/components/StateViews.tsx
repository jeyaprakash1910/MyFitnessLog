import { Link } from 'react-router-dom';
import { ApiError } from '@/api';
import { routes } from '@/app/routes';

/**
 * Shared loading / empty / error presentation.
 *
 * Kept in one place so every page states these the same way, and so each is a
 * plain function of props — no data fetching — which makes them trivially
 * testable and reusable in Phase 3.
 */

/**
 * Skeleton placeholder shown during the first load.
 *
 * A skeleton rather than a spinner: the list's shape is known ahead of time, so
 * showing it avoids the layout jump a spinner causes when data arrives.
 * `aria-hidden` keeps the decorative bars out of the accessibility tree, while
 * the live region announces loading to screen readers.
 */
export function LoadingList({ rows = 3 }: { rows?: number }) {
  return (
    <div data-testid="history-loading">
      <p className="sr-only" role="status">
        Loading workouts…
      </p>
      <ul className="space-y-3" aria-hidden="true">
        {Array.from({ length: rows }, (_, index) => (
          <li key={index} className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="flex justify-between">
              <div className="h-5 w-32 animate-pulse rounded bg-slate-200" />
              <div className="h-5 w-16 animate-pulse rounded bg-slate-200" />
            </div>
            <div className="mt-3 h-4 w-48 animate-pulse rounded bg-slate-100" />
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Shown when a requested workout is not part of history.
 *
 * "Not available" rather than "does not exist": the id may be perfectly real —
 * a discarded or in-progress workout — but history is defined as COMPLETED
 * only, so it is not available *here*. Android's wording is "This workout is no
 * longer available."; the extra sentence is the web-only affordance of a link
 * back, since a deep link is how a user most likely arrived.
 */
export function NotFoundState() {
  return (
    <div className="py-12 text-center" data-testid="workout-not-found">
      <p className="text-slate-700">This workout is no longer available.</p>
      <Link
        to={routes.history}
        className="focus-ring mt-4 inline-block rounded text-sm text-blue-700 underline"
      >
        Back to history
      </Link>
    </div>
  );
}

/**
 * Shown when the backend returns no workouts.
 *
 * The wording matters: an empty history is the *expected* state until a phone
 * syncs, so it explains where data comes from rather than looking broken
 * (MILESTONE_10_PLAN §8, risk 4). Android's equivalent says "Finish a workout to
 * see it here"; on web the missing step is the sync, not the workout.
 */
export function EmptyState() {
  return (
    <p className="py-12 text-center text-slate-500" data-testid="history-empty">
      No completed workouts yet. Workouts appear here after your phone syncs.
    </p>
  );
}

/** Maps an error to a message that tells the user what they can actually do. */
function describeError(error: Error): string {
  if (error instanceof ApiError) {
    switch (error.kind) {
      case 'network':
        return 'Could not reach the server. Check that the backend is running and try again.';
      case 'server':
        return 'The server had a problem loading your workouts. Please try again.';
      case 'client':
        return error.message;
      default:
        return 'Something went wrong loading your workouts.';
    }
  }
  return 'Something went wrong loading your workouts.';
}

/**
 * Error state with an explicit retry.
 *
 * A manual retry exists because the query client deliberately does not retry
 * 4xx responses, and because automatic retries have already been exhausted by
 * the time this renders — leaving the user with no way forward otherwise.
 */
export function ErrorState({ error, onRetry }: { error: Error; onRetry: () => void }) {
  return (
    <div className="py-12 text-center" role="alert" data-testid="history-error">
      <p className="text-slate-700">{describeError(error)}</p>
      <button
        type="button"
        onClick={onRetry}
        className="focus-ring mt-4 rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
      >
        Try again
      </button>
    </div>
  );
}
