package com.myfitnesslog.feature.history.data.local

import androidx.room.Embedded
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity

/**
 * Read-only projection for the history list: a completed [WorkoutSessionEntity]
 * plus the number of exercises it contains, computed in SQL (a single grouped
 * query) so the list avoids an N+1 of per-session exercise reads. It stores no
 * new data — the count is derived at query time from the snapshot tables.
 */
data class CompletedWorkoutSummary(
    @Embedded val session: WorkoutSessionEntity,
    val exerciseCount: Int,
)
