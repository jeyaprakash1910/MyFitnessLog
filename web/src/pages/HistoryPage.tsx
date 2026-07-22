import { EmptyState, ErrorState, LoadingList } from '@/components/StateViews';
import { WorkoutHistoryList } from '@/components/WorkoutHistoryList';
import { useDocumentTitle } from '@/app/useDocumentTitle';
import { useWorkoutHistory } from '@/queries/workoutQueries';

/**
 * Workout history — the app's landing page.
 *
 * Owns the query and picks one of four mutually exclusive states. The states are
 * ordered so that error wins over stale data: showing a silently stale list
 * after a failed refetch would misrepresent what the user is looking at.
 *
 * `status` is not rendered anywhere: the endpoint returns COMPLETED workouts
 * only, so a badge saying "Completed" on every row would carry no information.
 */
export function HistoryPage() {
  const { data, isPending, isError, error, refetch } = useWorkoutHistory();
  useDocumentTitle('Workout History');

  return (
    <section>
      <h1 className="text-xl font-semibold sm:text-2xl">Workout History</h1>
      <div className="mt-4">
        {isPending ? (
          <LoadingList />
        ) : isError ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.length === 0 ? (
          <EmptyState />
        ) : (
          <WorkoutHistoryList workouts={data} />
        )}
      </div>
    </section>
  );
}
