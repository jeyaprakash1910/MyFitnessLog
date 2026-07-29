package com.myfitnesslog.feature.history.data

import com.myfitnesslog.feature.history.data.local.CompletedWorkoutSummary
import com.myfitnesslog.feature.history.data.local.PreviousSetPerformance
import com.myfitnesslog.feature.history.data.local.WorkoutHistoryDao
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/**
 * Default [WorkoutHistoryRepository]. A thin read-only pass-through to
 * [WorkoutHistoryDao] — history has no writes, no timestamps to manage, and no
 * sync status to update, so the repository simply exposes the DAO's Flows behind
 * the feature boundary. Presentation models and derived values are built above
 * this layer.
 */
class WorkoutHistoryRepositoryImpl @Inject constructor(
    private val historyDao: WorkoutHistoryDao,
) : WorkoutHistoryRepository {

    override fun observeCompletedWorkouts(): Flow<List<WorkoutSessionEntity>> =
        historyDao.observeCompletedSessions()

    override fun observeCompletedWorkoutSummaries(): Flow<List<CompletedWorkoutSummary>> =
        historyDao.observeCompletedSummaries()

    override fun observeWorkout(sessionId: UUID): Flow<WorkoutSessionEntity?> =
        historyDao.observeCompletedSessionById(sessionId)

    override fun observeExercises(sessionId: UUID): Flow<List<WorkoutExerciseEntity>> =
        historyDao.observeExercisesForSession(sessionId)

    override fun observeSets(sessionId: UUID): Flow<List<WorkoutSetEntity>> =
        historyDao.observeSetsForSession(sessionId)

    override suspend fun getPreviousSets(exerciseId: UUID): List<PreviousSetPerformance> =
        historyDao.getPreviousSets(exerciseId)

    override suspend fun getPreviousSetsInRoutine(
        exerciseId: UUID,
        routineId: UUID?,
    ): List<PreviousSetPerformance> =
        historyDao.getPreviousSetsInRoutine(exerciseId, routineId)
}
