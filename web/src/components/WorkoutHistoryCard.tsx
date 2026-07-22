import { memo } from 'react';
import { Link } from 'react-router-dom';
import { workoutDetailPath } from '@/app/routes';

/**
 * One row of workout history, mirroring the Android card
 * (`WorkoutHistoryScreen.WorkoutHistoryRow`):
 *
 * ```
 * [date]                          [duration]
 * [type label] · ended [time]
 * [notes preview]
 * ```
 *
 * Android's middle line is `type · exercise count`. The count is not exposed by
 * the list endpoint, so the completion time takes that slot rather than forcing
 * an N+1 fetch or a backend change.
 *
 * Props are pre-formatted strings, not a session object. That mirrors Android,
 * where the ViewModel formats and the composable only renders — it keeps
 * `Intl` work out of render, and makes `memo` effective because the props are
 * primitives compared by value.
 *
 * The whole card is a `Link`, so keyboard focus, Enter/click, middle-click and
 * "open in new tab" all work for free — a `div` with `onClick` would lose all of
 * that.
 */
export interface WorkoutHistoryCardProps {
  id: string;
  date: string;
  duration: string;
  typeLabel: string;
  completedAt: string;
  notesPreview: string | null;
}

function WorkoutHistoryCardComponent({
  id,
  date,
  duration,
  typeLabel,
  completedAt,
  notesPreview,
}: WorkoutHistoryCardProps) {
  return (
    <li>
      <Link
        to={workoutDetailPath(id)}
        aria-label={`View workout details for ${date}`}
        data-testid={`history-row-${id}`}
        className="focus-ring block rounded-lg border border-slate-200 bg-white p-4 transition hover:border-slate-300 hover:shadow-sm sm:p-5"
      >
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <span className="font-semibold text-slate-900">{date}</span>
          <span className="font-semibold tabular-nums text-slate-900">{duration}</span>
        </div>
        <p className="mt-1 text-sm text-slate-600">
          {typeLabel} · ended {completedAt}
        </p>
        {notesPreview !== null && (
          <p className="mt-1 truncate text-sm text-slate-500">{notesPreview}</p>
        )}
      </Link>
    </li>
  );
}

/**
 * Memoized: the list re-renders whenever the query refetches (every window
 * focus past the stale time), and an unchanged workout is genuinely unchanged —
 * completed workouts are immutable on the backend.
 */
export const WorkoutHistoryCard = memo(WorkoutHistoryCardComponent);
