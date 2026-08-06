package com.myfitnesslog.core.sync.restore

import android.util.Log
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.exercise.data.ExerciseCategoryRepository
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.routine.data.local.RoutineDao
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDao
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionDao
import com.myfitnesslog.feature.workout.data.local.WorkoutSetDao
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [RestoreManager].
 *
 * The whole of the design is in the order things happen and in what is allowed to
 * fail, so both are spelled out below rather than left to be inferred from the
 * code.
 */
@Singleton
class RestoreManagerImpl @Inject constructor(
    private val routineApi: RoutineApi,
    private val workoutSessionApi: WorkoutSessionApi,
    private val exerciseCategoryRepository: ExerciseCategoryRepository,
    private val exerciseRepository: ExerciseRepository,
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val workoutSetDao: WorkoutSetDao,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RestoreManager {

    override suspend fun restoreIfEmpty(): RestoreOutcome = withContext(ioDispatcher) {
        if (routineDao.count() > 0 || workoutSessionDao.count() > 0) {
            return@withContext RestoreOutcome.NotNeeded
        }

        try {
            restore()
        } catch (e: IOException) {
            // The everyday case: no signal, or the instance is asleep.
            Log.i(TAG, "Restore skipped, backend unreachable: ${e.message}")
            RestoreOutcome.Failed("Backend unreachable")
        } catch (e: RuntimeException) {
            // A malformed response, an unparseable id, an unexpected shape. Still
            // not worth breaking a launch over: the user simply starts empty, as
            // they would have anyway.
            Log.w(TAG, "Restore failed", e)
            RestoreOutcome.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private suspend fun restore(): RestoreOutcome {
        val restoredAt = clock.instant()

        // Order is a correctness requirement, not tidiness. Room enforces it:
        //
        //   1. Exercise categories, then exercises. Both routine_exercise and
        //      workout_exercise reference exercise with ON DELETE RESTRICT, so an
        //      exercise that is not present locally makes its parent row
        //      un-insertable.
        //   2. Routines before sessions. workout_session references routine with
        //      ON DELETE SET NULL, so a session restored first would keep its
        //      routineId but point at nothing.
        //
        // Getting this wrong does not fail loudly; it silently drops the link
        // between a workout and the routine it came from.
        exerciseCategoryRepository.refresh()
        exerciseRepository.refreshLibrary()

        val routineSummaries = routineApi.getRoutines()
        var restoredRoutines = 0
        for (summary in routineSummaries) {
            val detail = routineApi.getRoutine(summary.id)
            val routineId = UUID.fromString(detail.id)
            routineDao.upsert(routineEntity(detail, restoredAt))
            routineExerciseDao.upsertAll(
                detail.exercises.map { routineExerciseEntity(it, routineId, restoredAt) },
            )
            restoredRoutines++
        }

        val sessionSummaries = workoutSessionApi.getWorkoutSessions()
        var restoredSessions = 0
        for (summary in sessionSummaries) {
            val detail = workoutSessionApi.getWorkoutSession(summary.id)
            val sessionId = UUID.fromString(detail.id)
            workoutSessionDao.upsert(workoutSessionEntity(detail, restoredAt))

            for (exercise in detail.exercises) {
                val workoutExerciseId = UUID.fromString(exercise.id)
                workoutExerciseDao.upsert(
                    workoutExerciseEntity(exercise, sessionId, restoredAt),
                )
                workoutSetDao.upsertAll(
                    exercise.sets.map { workoutSetEntity(it, workoutExerciseId, restoredAt) },
                )
            }
            restoredSessions++
        }

        return if (restoredRoutines == 0 && restoredSessions == 0) {
            RestoreOutcome.NothingToRestore
        } else {
            Log.i(TAG, "Restored $restoredRoutines routines and $restoredSessions workouts")
            RestoreOutcome.Restored(routines = restoredRoutines, sessions = restoredSessions)
        }
    }

    private companion object {
        const val TAG = "RestoreManager"
    }
}
