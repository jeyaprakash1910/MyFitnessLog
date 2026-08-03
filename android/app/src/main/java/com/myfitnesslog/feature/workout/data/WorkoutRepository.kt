package com.myfitnesslog.feature.workout.data

import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

/**
 * Repository boundary for logging an active workout (mutable repository, local
 * only). Starting a workout is owned by StartWorkoutUseCase; this repository
 * manages the active session's sets and its completion/discard lifecycle.
 *
 * Immutability rule: once a session is COMPLETED (or DISCARDED) its sets can no
 * longer be added/updated/deleted and its lifecycle cannot transition again.
 * Such attempts throw [IllegalStateException].
 */
interface WorkoutRepository {

    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    /** Observes a specific session through its lifecycle (including completion). */
    fun observeSession(sessionId: UUID): Flow<WorkoutSessionEntity?>

    fun observeWorkoutExercises(sessionId: UUID): Flow<List<WorkoutExerciseEntity>>

    fun observeSets(workoutExerciseId: UUID): Flow<List<WorkoutSetEntity>>

    /**
     * Adds an exercise to an in-progress workout (used by manual workouts). The
     * [exerciseName] is the master exercise name captured at selection time (the
     * same snapshot semantics as routine-based workouts). Returns the new
     * WorkoutExercise id. Rejected if the session is not IN_PROGRESS.
     */
    suspend fun addExercise(sessionId: UUID, exerciseId: UUID, exerciseName: String): UUID

    /** Appends a set to the exercise (setNumber auto-assigned). Returns its id. */
    suspend fun addSet(
        workoutExerciseId: UUID,
        weight: BigDecimal,
        repetitions: Int,
        setCategory: SetCategory = SetCategory.WORKING,
        rpe: BigDecimal? = null,
        rir: BigDecimal? = null,
    ): UUID

    suspend fun updateSet(
        setId: UUID,
        weight: BigDecimal,
        repetitions: Int,
        setCategory: SetCategory,
        rpe: BigDecimal?,
        rir: BigDecimal?,
        isCompleted: Boolean,
    )

    suspend fun deleteSet(setId: UUID)

    /**
     * Sets the rest duration (seconds) applied when a set of this exercise is
     * completed (spec §5). Session-scoped: the routine template is never modified.
     * Rejected unless the owning session is IN_PROGRESS.
     */
    suspend fun updateExerciseRest(workoutExerciseId: UUID, restSeconds: Int)

    /**
     * Sets the free-text note on an exercise within its (in-progress) session.
     * Blank input is stored as null. Session-scoped: the routine template is never
     * modified. Rejected unless the owning session is IN_PROGRESS.
     */
    suspend fun updateExerciseNotes(workoutExerciseId: UUID, notes: String?)

    /**
     * Moves an exercise up or down within its (in-progress) session by swapping its
     * `exerciseOrder` with the adjacent exercise. Session-scoped only — the routine
     * template is never modified (V2 Exercise Ordering Contract). No-op at a boundary.
     */
    suspend fun moveExercise(workoutExerciseId: UUID, up: Boolean)

    /**
     * Removes an exercise from its (in-progress) session, deleting its sets. Completed
     * sets are tombstoned (ADR-0007) so their deletions propagate. Session-scoped only;
     * the routine is untouched.
     */
    suspend fun removeExercise(workoutExerciseId: UUID)

    suspend fun completeWorkout(sessionId: UUID)

    suspend fun discardWorkout(sessionId: UUID)
}
