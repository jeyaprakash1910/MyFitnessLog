package com.myfitnesslog.feature.workout.domain

import androidx.room.withTransaction
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.routine.data.RoutineRepository
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseDao
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Starts (or resumes) a workout from a routine — the first non-trivial business
 * rule in the app, so it lives in a dedicated use case rather than a repository
 * method (docs/ANDROID_ARCHITECTURE.md: add UseCases incrementally).
 *
 * Rules:
 *  - Single active session: if a workout is already IN_PROGRESS, that session is
 *    resumed (its id returned) and nothing new is created.
 *  - Otherwise a new IN_PROGRESS session is created. If a [routineId] is given,
 *    the routine's exercises are copied into immutable [WorkoutExerciseEntity]
 *    snapshots — carrying the exercise name and all planned targets — so later
 *    routine edits never change this workout (DATABASE.md snapshot architecture).
 *  - A null [routineId] starts a manual (ad-hoc) workout: the session has
 *    routineId = null and no exercises; the user adds them during the workout.
 *  - The session is left ready for set logging.
 */
class StartWorkoutUseCase @Inject constructor(
    private val database: MyFitnessLogDatabase,
    private val workoutSessionDao: WorkoutSessionDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val routineRepository: RoutineRepository,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Returns the id of the active workout — resumed or newly created. Pass a
     * routineId to start from a routine, or null for a manual workout.
     */
    suspend operator fun invoke(routineId: UUID?): UUID = withContext(ioDispatcher) {
        workoutSessionDao.getActive()?.let { return@withContext it.id }

        val now = clock.instant()
        val sessionId = UUID.randomUUID()
        val session = WorkoutSessionEntity(
            id = sessionId,
            routineId = routineId,
            status = WorkoutStatus.IN_PROGRESS,
            startedAt = now,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        val details = if (routineId != null) routineRepository.getRoutineExerciseDetails(routineId) else emptyList()
        val snapshots = details.map { detail ->
            val planned = detail.routineExercise
            WorkoutExerciseEntity(
                id = UUID.randomUUID(),
                workoutSessionId = sessionId,
                exerciseId = planned.exerciseId,
                exerciseName = detail.exerciseName,
                exerciseOrder = planned.displayOrder,
                targetSets = planned.targetSets,
                minTargetReps = planned.minTargetReps,
                maxTargetReps = planned.maxTargetReps,
                targetRestSeconds = planned.targetRestSeconds,
                notes = planned.notes,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            )
        }

        // Atomic: the session and its exercise snapshots must be created together.
        // If any insert fails (e.g. an invalid routine FK), the transaction rolls
        // back so no partially-created, exercise-less workout is left behind.
        database.withTransaction {
            workoutSessionDao.upsert(session)
            if (snapshots.isNotEmpty()) workoutExerciseDao.upsertAll(snapshots)
        }

        sessionId
    }
}
