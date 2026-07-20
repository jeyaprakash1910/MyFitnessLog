package com.myfitnesslog.feature.workout.data

import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseDao
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Default [WorkoutRepository]. Owns timestamp management, syncStatus updates,
 * value validation, and — critically — the completed-workout immutability rule:
 * every mutation first checks that the owning session is still IN_PROGRESS.
 */
class WorkoutRepositoryImpl @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val exerciseDao: WorkoutExerciseDao,
    private val setDao: WorkoutSetDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : WorkoutRepository {

    override fun observeActiveSession(): Flow<WorkoutSessionEntity?> = sessionDao.observeActive()

    override fun observeSession(sessionId: UUID): Flow<WorkoutSessionEntity?> =
        sessionDao.observeById(sessionId)

    override fun observeWorkoutExercises(sessionId: UUID): Flow<List<WorkoutExerciseEntity>> =
        exerciseDao.observeBySession(sessionId)

    override fun observeSets(workoutExerciseId: UUID): Flow<List<WorkoutSetEntity>> =
        setDao.observeByExercise(workoutExerciseId)

    override suspend fun addExercise(
        sessionId: UUID,
        exerciseId: UUID,
        exerciseName: String,
    ): UUID = withContext(ioDispatcher) {
        requireInProgress(sessionId)
        val now = clock.instant()
        val nextOrder = (exerciseDao.getBySession(sessionId).maxOfOrNull { it.exerciseOrder } ?: -1) + 1
        val id = UUID.randomUUID()
        exerciseDao.upsert(
            WorkoutExerciseEntity(
                id = id,
                workoutSessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                exerciseOrder = nextOrder,
                // Manual exercises have no routine targets; use sensible defaults
                // (they satisfy the NOT NULL / check constraints and can be edited
                // per set during logging).
                targetSets = DEFAULT_TARGET_SETS,
                minTargetReps = DEFAULT_MIN_REPS,
                maxTargetReps = DEFAULT_MAX_REPS,
                targetRestSeconds = null,
                notes = null,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        id
    }

    override suspend fun addSet(
        workoutExerciseId: UUID,
        weight: BigDecimal,
        repetitions: Int,
        setCategory: SetCategory,
        rpe: BigDecimal?,
        rir: BigDecimal?,
    ): UUID = withContext(ioDispatcher) {
        validate(weight, repetitions, rpe, rir)
        val exercise = exerciseDao.getById(workoutExerciseId)
            ?: error("Workout exercise $workoutExerciseId not found")
        requireInProgress(exercise.workoutSessionId)

        val now = clock.instant()
        val nextNumber = (setDao.getByExercise(workoutExerciseId).maxOfOrNull { it.setNumber } ?: 0) + 1
        val id = UUID.randomUUID()
        setDao.upsert(
            WorkoutSetEntity(
                id = id,
                workoutExerciseId = workoutExerciseId,
                setNumber = nextNumber,
                weight = weight,
                repetitions = repetitions,
                setCategory = setCategory,
                rpe = rpe,
                rir = rir,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        id
    }

    override suspend fun updateSet(
        setId: UUID,
        weight: BigDecimal,
        repetitions: Int,
        setCategory: SetCategory,
        rpe: BigDecimal?,
        rir: BigDecimal?,
        isCompleted: Boolean,
    ) = withContext(ioDispatcher) {
        validate(weight, repetitions, rpe, rir)
        val existing = setDao.getById(setId) ?: error("Set $setId not found")
        requireInProgressForSet(existing.workoutExerciseId)
        setDao.upsert(
            existing.copy(
                weight = weight,
                repetitions = repetitions,
                setCategory = setCategory,
                rpe = rpe,
                rir = rir,
                isCompleted = isCompleted,
                updatedAt = clock.instant(),
                syncStatus = SyncStatus.PENDING,
            ),
        )
    }

    override suspend fun deleteSet(setId: UUID) = withContext(ioDispatcher) {
        val existing = setDao.getById(setId) ?: return@withContext
        requireInProgressForSet(existing.workoutExerciseId)
        setDao.deleteById(setId)
    }

    override suspend fun completeWorkout(sessionId: UUID) =
        finishWorkout(sessionId, WorkoutStatus.COMPLETED)

    override suspend fun discardWorkout(sessionId: UUID) =
        finishWorkout(sessionId, WorkoutStatus.DISCARDED)

    private suspend fun finishWorkout(sessionId: UUID, target: WorkoutStatus) =
        withContext(ioDispatcher) {
            val session = requireInProgress(sessionId)
            val now = clock.instant()
            sessionDao.upsert(
                session.copy(
                    status = target,
                    endedAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
        }

    private suspend fun requireInProgressForSet(workoutExerciseId: UUID) {
        val exercise = exerciseDao.getById(workoutExerciseId)
            ?: error("Workout exercise $workoutExerciseId not found")
        requireInProgress(exercise.workoutSessionId)
    }

    /** Returns the session iff it is IN_PROGRESS; otherwise rejects the mutation. */
    private suspend fun requireInProgress(sessionId: UUID): WorkoutSessionEntity {
        val session = sessionDao.getById(sessionId) ?: error("Workout session $sessionId not found")
        check(session.status == WorkoutStatus.IN_PROGRESS) {
            "Workout ${session.status} is immutable and cannot be modified"
        }
        return session
    }

    private fun validate(weight: BigDecimal, repetitions: Int, rpe: BigDecimal?, rir: BigDecimal?) {
        require(weight.signum() >= 0) { "weight must be >= 0" }
        require(repetitions >= 0) { "repetitions must be >= 0" }
        rpe?.let { require(it >= BigDecimal.ONE && it <= BigDecimal.TEN) { "rpe must be between 1 and 10" } }
        rir?.let { require(it.signum() >= 0) { "rir must be >= 0" } }
    }

    private companion object {
        const val DEFAULT_TARGET_SETS = 3
        const val DEFAULT_MIN_REPS = 8
        const val DEFAULT_MAX_REPS = 12
    }
}
