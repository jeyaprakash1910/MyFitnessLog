package com.myfitnesslog.feature.workout.data

import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.sync.SyncTrigger
import com.myfitnesslog.core.sync.source.WorkoutExerciseSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSessionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSetDeletionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSetSyncSource
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.workout.data.local.PendingWorkoutSet
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseDao
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetTombstoneEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [WorkoutRepository]. Owns timestamp management, syncStatus updates,
 * value validation, and — critically — the completed-workout immutability rule:
 * every mutation first checks that the owning session is still IN_PROGRESS.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val exerciseDao: WorkoutExerciseDao,
    private val setDao: WorkoutSetDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val syncTrigger: SyncTrigger,
) : WorkoutRepository,
    WorkoutSessionSyncSource,
    WorkoutExerciseSyncSource,
    WorkoutSetSyncSource,
    WorkoutSetDeletionSyncSource {

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
        syncTrigger.requestSync()
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
        syncTrigger.requestSync()
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
        syncTrigger.requestSync()
    }

    override suspend fun deleteSet(setId: UUID) = withContext(ioDispatcher) {
        val existing = setDao.getById(setId) ?: return@withContext
        val session = requireInProgressForSet(existing.workoutExerciseId)
        // A set is hard-deleted, so nothing would be left to upload. The
        // tombstone is what the backend is told about (ADR-0007); it is written
        // in the same transaction as the delete so the two cannot diverge.
        setDao.deleteAndRecord(
            WorkoutSetTombstoneEntity(
                workoutSetId = setId,
                workoutExerciseId = existing.workoutExerciseId,
                workoutSessionId = session.id,
                deletedAt = clock.instant(),
            ),
        )
        syncTrigger.requestSync()
    }

    override suspend fun updateExerciseRest(workoutExerciseId: UUID, restSeconds: Int) = withContext(ioDispatcher) {
        require(restSeconds >= 0) { "restSeconds must be >= 0" }
        val exercise = exerciseDao.getById(workoutExerciseId) ?: return@withContext
        requireInProgress(exercise.workoutSessionId)
        exerciseDao.upsert(
            exercise.copy(
                targetRestSeconds = restSeconds,
                updatedAt = clock.instant(),
                syncStatus = SyncStatus.PENDING,
            ),
        )
        syncTrigger.requestSync()
    }

    override suspend fun updateExerciseNotes(workoutExerciseId: UUID, notes: String?) = withContext(ioDispatcher) {
        val exercise = exerciseDao.getById(workoutExerciseId) ?: return@withContext
        requireInProgress(exercise.workoutSessionId)
        exerciseDao.upsert(
            exercise.copy(
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = clock.instant(),
                syncStatus = SyncStatus.PENDING,
            ),
        )
        syncTrigger.requestSync()
    }

    override suspend fun moveExercise(workoutExerciseId: UUID, up: Boolean) = withContext(ioDispatcher) {
        val exercise = exerciseDao.getById(workoutExerciseId) ?: return@withContext
        val session = requireInProgress(exercise.workoutSessionId)
        val ordered = exerciseDao.getBySession(session.id) // sorted by exerciseOrder ASC
        val index = ordered.indexOfFirst { it.id == workoutExerciseId }
        if (index < 0) return@withContext
        val swapIndex = if (up) index - 1 else index + 1
        if (swapIndex !in ordered.indices) return@withContext // boundary: no-op

        val a = ordered[index]
        val b = ordered[swapIndex]
        val now = clock.instant()
        // Swap the two rows' order values (session state only; routine untouched).
        exerciseDao.upsertAll(
            listOf(
                a.copy(exerciseOrder = b.exerciseOrder, updatedAt = now, syncStatus = SyncStatus.PENDING),
                b.copy(exerciseOrder = a.exerciseOrder, updatedAt = now, syncStatus = SyncStatus.PENDING),
            ),
        )
        syncTrigger.requestSync()
    }

    override suspend fun removeExercise(workoutExerciseId: UUID) = withContext(ioDispatcher) {
        val exercise = exerciseDao.getById(workoutExerciseId) ?: return@withContext
        val session = requireInProgress(exercise.workoutSessionId)
        val now = clock.instant()
        // Tombstone each set (ADR-0007) so persisted deletions converge on the backend,
        // then delete the exercise (its remaining rows cascade in Room).
        setDao.getByExercise(workoutExerciseId).forEach { set ->
            setDao.deleteAndRecord(
                WorkoutSetTombstoneEntity(
                    workoutSetId = set.id,
                    workoutExerciseId = workoutExerciseId,
                    workoutSessionId = session.id,
                    deletedAt = now,
                ),
            )
        }
        exerciseDao.deleteById(workoutExerciseId)
        syncTrigger.requestSync()
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
            // The terminal transition is the highest-value thing to upload: it
            // is what seals the workout on the backend.
            syncTrigger.requestSync()
        }

    private suspend fun requireInProgressForSet(workoutExerciseId: UUID): WorkoutSessionEntity {
        val exercise = exerciseDao.getById(workoutExerciseId)
            ?: error("Workout exercise $workoutExerciseId not found")
        return requireInProgress(exercise.workoutSessionId)
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
    // ---- Synchronization surface (core/sync) -------------------------------
    //
    // See the equivalent block in RoutineRepositoryImpl. These methods write
    // only syncStatus and never touch updatedAt: recording a sync is not a user
    // edit, and stamping the clock here would re-dirty the row forever.

    override suspend fun getPendingWorkoutSessions(): List<WorkoutSessionEntity> =
        withContext(ioDispatcher) { sessionDao.getPendingSync() }

    override suspend fun setWorkoutSessionSyncStatus(id: UUID, status: SyncStatus) =
        withContext(ioDispatcher) { sessionDao.updateSyncStatus(id, status) }

    override suspend fun getPendingWorkoutExercises(): List<WorkoutExerciseEntity> =
        withContext(ioDispatcher) { exerciseDao.getPendingSync() }

    override suspend fun setWorkoutExerciseSyncStatus(id: UUID, status: SyncStatus) =
        withContext(ioDispatcher) { exerciseDao.updateSyncStatus(id, status) }

    override suspend fun getPendingWorkoutSets(): List<PendingWorkoutSet> =
        withContext(ioDispatcher) { setDao.getPendingSync() }

    override suspend fun setWorkoutSetSyncStatus(id: UUID, status: SyncStatus) =
        withContext(ioDispatcher) { setDao.updateSyncStatus(id, status) }

    override suspend fun getPendingWorkoutSetDeletions(): List<WorkoutSetTombstoneEntity> =
        withContext(ioDispatcher) { setDao.getPendingTombstones() }

    override suspend fun clearWorkoutSetDeletion(workoutSetId: UUID) =
        withContext(ioDispatcher) { setDao.deleteTombstone(workoutSetId) }

    /** Recovers all three workout tables. See RoutineRepositoryImpl for why one
     * override covers every workout SyncSource this class implements. */
    override suspend fun recoverStaleSyncing(): Int = withContext(ioDispatcher) {
        sessionDao.recoverStaleSyncing() +
            exerciseDao.recoverStaleSyncing() +
            setDao.recoverStaleSyncing()
    }
}
