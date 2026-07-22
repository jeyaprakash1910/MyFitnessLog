import { Link, useParams } from 'react-router-dom';
import { ApiError } from '@/api';
import { routes } from '@/app/routes';
import { ErrorState, LoadingList, NotFoundState } from '@/components/StateViews';
import { WorkoutDetailView } from '@/components/WorkoutDetailView';
import { useDocumentTitle } from '@/app/useDocumentTitle';
import { formatWorkoutDate } from '@/format/history';
import { useWorkoutDetail } from '@/queries/workoutQueries';

/**
 * Read-only workout detail.
 *
 * Reachable by navigation from history and by direct URL — both are just the
 * route, since the page fetches from the id in the path and holds no state
 * handed over by the list. That is why a deep link works with no extra effort,
 * and why it must decide for itself what is viewable.
 *
 * ## State selection
 *
 * ```
 * no id in path        → Not Found
 * loading              → skeleton
 * 404 from backend     → Not Found
 * other error          → error + retry
 * not COMPLETED (null) → Not Found
 * otherwise            → the snapshot
 * ```
 *
 * The two Not Found paths are deliberately indistinguishable to the user: "this
 * workout does not exist" and "this workout is not part of history" are the same
 * answer from the web client's point of view, and separating them would leak
 * that a discarded workout exists — the very thing history hides.
 *
 * Only one request is made. The backend assembles the full nested snapshot
 * (session → exercises → sets) in a single transaction, so there is nothing to
 * fetch per exercise; child components receive data as props and never fetch.
 */
export function WorkoutDetailPage() {
  const { workoutId } = useParams<{ workoutId: string }>();
  // The route cannot match without the param, but `useParams` types it optional.
  // Treating a missing id as Not Found is safer than asserting it is present.
  const { data, isPending, isError, error, refetch } = useWorkoutDetail(workoutId ?? '');

  const notFound = isError && error instanceof ApiError && error.isNotFound;

  // Null until the workout resolves, so the tab shows the base title rather
  // than flashing a placeholder while loading.
  useDocumentTitle(data ? `Workout · ${formatWorkoutDate(data.startedAt)}` : null);

  return (
    <section>
      <Link
        to={routes.history}
        className="focus-ring -m-1 inline-block rounded p-1 text-sm text-slate-600 underline hover:text-slate-900"
      >
        <span aria-hidden="true">←</span> Back to history
      </Link>
      <div className="mt-4">
        {workoutId === undefined ? (
          <NotFoundState />
        ) : isPending ? (
          <LoadingList rows={2} />
        ) : notFound ? (
          <NotFoundState />
        ) : isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data === null ? (
          <NotFoundState />
        ) : (
          <WorkoutDetailView workout={data} />
        )}
      </div>
    </section>
  );
}
