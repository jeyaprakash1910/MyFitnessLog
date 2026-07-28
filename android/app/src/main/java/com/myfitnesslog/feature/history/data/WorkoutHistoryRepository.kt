package com.myfitnesslog.feature.history.data

import com.myfitnesslog.feature.history.data.local.CompletedWorkoutSummary
import com.myfitnesslog.feature.history.data.local.PreviousSetPerformance
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Read-only repository boundary for Workout History (Milestone 7).
 *
 * History reads completed workouts from the immutable snapshot tables and never
 * mutates them: it exposes only `observe*` Flows and has no write operations.
 * Only COMPLETED sessions are surfaced (DISCARDED and IN_PROGRESS are excluded),
 * newest completed workout first. Derived values such as duration or set counts
 * are computed by the presentation layer, never stored (Entity Purity, §9).
 */
interface WorkoutHistoryRepository {

    /** Completed workouts, newest first (by startedAt). */
    fun observeCompletedWorkouts(): Flow<List<WorkoutSessionEntity>>

    /**
     * Completed workouts with their exercise count for the history list, newest
     * first. Used by the list screen so the exercise count comes from one grouped
     * query rather than a per-session read.
     */
    fun observeCompletedWorkoutSummaries(): Flow<List<CompletedWorkoutSummary>>

    /** A single completed workout for the detail screen; null if not found/completed. */
    fun observeWorkout(sessionId: UUID): Flow<WorkoutSessionEntity?>

    /** The snapshotted exercises of a workout, in performed order. */
    fun observeExercises(sessionId: UUID): Flow<List<WorkoutExerciseEntity>>

    /** Every set performed in a workout, ordered by exercise then set number. */
    fun observeSets(sessionId: UUID): Flow<List<WorkoutSetEntity>>

    /**
     * The previous performance for an exercise: the sets from the most recent
     * COMPLETED workout containing it (cross-routine), for the PREVIOUS column
     * (V2 Milestone E). Read-only projection; empty when there is no history.
     */
    suspend fun getPreviousSets(exerciseId: UUID): List<PreviousSetPerformance>
}
