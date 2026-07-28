package com.myfitnesslog.feature.history.data.local

import androidx.room.Dao
import androidx.room.Query
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Read-only DAO for Workout History (Milestone 7). It queries the existing
 * workout-history snapshot tables (`workout_session`, `workout_exercise`,
 * `workout_set`) without introducing new tables, columns, or schema changes, and
 * reuses the existing Room entities.
 *
 * History is intentionally a separate feature with separate read concerns, so
 * these queries live here rather than swelling the active-logging DAOs in the
 * workout feature. All reads are Flows (the source of truth the UI observes).
 *
 * Only COMPLETED sessions are considered history: IN_PROGRESS workouts are still
 * being logged, and DISCARDED workouts are hidden from the user (ANDROID_
 * ARCHITECTURE §13). Sessions are ordered newest-first by `startedAt` — the same
 * ordering convention the active-session queries use and the column that
 * `workout_session` is indexed on.
 */
@Dao
interface WorkoutHistoryDao {

    @Query(
        "SELECT * FROM workout_session WHERE status = 'COMPLETED' ORDER BY startedAt DESC",
    )
    fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    /**
     * Completed sessions with their exercise count for the history list, newest
     * first. A LEFT JOIN keeps sessions that have no exercises (count 0); the
     * count is computed per session in one query rather than per-row follow-ups.
     */
    @Query(
        """
        SELECT s.*, COUNT(e.id) AS exerciseCount
        FROM workout_session AS s
        LEFT JOIN workout_exercise AS e ON e.workoutSessionId = s.id
        WHERE s.status = 'COMPLETED'
        GROUP BY s.id
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeCompletedSummaries(): Flow<List<CompletedWorkoutSummary>>

    /**
     * Observes a single completed session for the detail screen. Filtered to
     * COMPLETED so in-progress/discarded sessions are never surfaced through
     * history; emits null if no such completed session exists.
     */
    @Query(
        "SELECT * FROM workout_session WHERE id = :sessionId AND status = 'COMPLETED'",
    )
    fun observeCompletedSessionById(sessionId: UUID): Flow<WorkoutSessionEntity?>

    /** The snapshotted exercises of a session, in performed order. */
    @Query(
        "SELECT * FROM workout_exercise WHERE workoutSessionId = :sessionId ORDER BY exerciseOrder ASC",
    )
    fun observeExercisesForSession(sessionId: UUID): Flow<List<WorkoutExerciseEntity>>

    /**
     * Every set performed in a session, joined through its parent exercise and
     * ordered by exercise then set number, so the detail screen can group sets
     * under their exercise from a single stream.
     */
    @Query(
        """
        SELECT s.* FROM workout_set AS s
        INNER JOIN workout_exercise AS e ON s.workoutExerciseId = e.id
        WHERE e.workoutSessionId = :sessionId
        ORDER BY e.exerciseOrder ASC, s.setNumber ASC
        """,
    )
    fun observeSetsForSession(sessionId: UUID): Flow<List<WorkoutSetEntity>>

    /**
     * The sets performed for [exerciseId] in the **most recent COMPLETED** workout
     * that contained it (regardless of routine) — the "previous performance" read
     * (V2 Milestone E, spec §6). Read-only; IN_PROGRESS/DISCARDED sessions are
     * excluded, so today's session never matches itself. Returns an empty list when
     * the exercise has no completed history. Ordered by set number.
     */
    @Query(
        """
        SELECT s.setNumber AS setNumber, s.weight AS weight, s.repetitions AS repetitions, s.rpe AS rpe
        FROM workout_set AS s
        INNER JOIN workout_exercise AS e ON s.workoutExerciseId = e.id
        WHERE e.exerciseId = :exerciseId
          AND e.workoutSessionId = (
            SELECT ss.id FROM workout_session AS ss
            INNER JOIN workout_exercise AS ee ON ee.workoutSessionId = ss.id
            WHERE ee.exerciseId = :exerciseId AND ss.status = 'COMPLETED'
            ORDER BY ss.startedAt DESC
            LIMIT 1
          )
        ORDER BY s.setNumber ASC
        """,
    )
    suspend fun getPreviousSets(exerciseId: UUID): List<PreviousSetPerformance>
}
