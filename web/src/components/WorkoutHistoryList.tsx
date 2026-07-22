import { useMemo } from 'react';
import type { WorkoutSessionSummary } from '@/api';
import { WorkoutHistoryCard } from './WorkoutHistoryCard';
import {
  formatCompletedDuration,
  formatTimeOfDay,
  formatWorkoutDate,
  sanitizeNotes,
  workoutTypeLabel,
} from '@/format/history';

/**
 * Renders the history rows.
 *
 * Deliberately a pure function of `workouts` with no data fetching: the page
 * owns the query, this owns presentation. That split is what lets the list be
 * tested against fixtures without a query client, and it is the seam pagination
 * will use — a paginated page passes a page's worth of items here and this
 * component does not change.
 *
 * Order is **not** applied here. The backend returns newest-first
 * (API_SPECIFICATION §7) and re-sorting client-side would duplicate a rule that
 * already has one home.
 */
export function WorkoutHistoryList({ workouts }: { workouts: WorkoutSessionSummary[] }) {
  // Formatting is Intl work over every row; doing it in a memo keeps it off
  // re-renders that do not change the data (a refetch returning identical rows
  // still produces a new array identity, but that is the only trigger).
  const items = useMemo(
    () =>
      workouts.map((workout) => ({
        id: workout.id,
        date: formatWorkoutDate(workout.startedAt),
        duration: formatCompletedDuration(workout.startedAt, workout.endedAt),
        typeLabel: workoutTypeLabel(workout.routineId),
        completedAt: workout.endedAt === null ? '—' : formatTimeOfDay(workout.endedAt),
        notesPreview: sanitizeNotes(workout.notes),
      })),
    [workouts],
  );

  return (
    <ul className="space-y-3" data-testid="history-list">
      {items.map((item) => (
        <WorkoutHistoryCard key={item.id} {...item} />
      ))}
    </ul>
  );
}
