package com.myfitnesslog.core.sync.restore

import android.util.Log
import com.myfitnesslog.core.data.local.SyncStatus
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
 * Default [RefreshManager].
 *
 * Three rules govern everything here, and each exists to prevent a specific way
 * of destroying the user's data:
 *
 * 1. **Defer while the outbox has work.** If anything is still waiting to upload,
 *    the backend is known to be behind and its answer is not worth having.
 * 2. **Never touch a row that is not SYNCED.** Even after the outbox drains, a row
 *    can be dirtied at any moment by someone using the app. Checking per row, not
 *    just once up front, closes that window.
 * 3. **Only reconcile removals within what the backend authoritatively lists.**
 *    Absence is only evidence of deletion inside a scope the endpoint promises to
 *    enumerate completely.
 */
@Singleton
class RefreshManagerImpl @Inject constructor(
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
) : RefreshManager {

    override suspend fun refresh(): RefreshOutcome = withContext(ioDispatcher) {
        if (hasPendingUploads()) {
            return@withContext RefreshOutcome.Deferred
        }
        try {
            apply()
        } catch (e: IOException) {
            Log.i(TAG, "Refresh skipped, backend unreachable: ${e.message}")
            RefreshOutcome.Failed("Backend unreachable")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Refresh failed", e)
            RefreshOutcome.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * Whether anything at all is still waiting to reach the backend.
     *
     * Tombstones count. A deletion that has not been replayed means the backend
     * still lists a row the user removed, and refreshing then would download that
     * row and put it straight back on the device. That is exactly the resurrection
     * TD-014 was fixed to prevent, and it would reappear here if deletions were
     * not part of this check.
     */
    private suspend fun hasPendingUploads(): Boolean =
        routineDao.getPendingSync().isNotEmpty() ||
            routineExerciseDao.getPendingSync().isNotEmpty() ||
            workoutSessionDao.getPendingSync().isNotEmpty() ||
            workoutExerciseDao.getPendingSync().isNotEmpty() ||
            workoutSetDao.getPendingSync().isNotEmpty() ||
            workoutSetDao.getPendingTombstones().isNotEmpty() ||
            workoutExerciseDao.getPendingTombstones().isNotEmpty()

    private suspend fun apply(): RefreshOutcome {
        val refreshedAt = clock.instant()
        var routinesUpdated = 0
        var routinesRemoved = 0
        var sessionsUpdated = 0
        var sessionsRemoved = 0
        var skipped = 0

        // Reference data first: routine and workout rows both hold a RESTRICT
        // foreign key to exercise, so an exercise added on another device has to
        // exist locally before anything can point at it.
        exerciseCategoryRepository.refresh()
        exerciseRepository.refreshLibrary()

        // --- routines ---------------------------------------------------
        val remoteRoutines = routineApi.getRoutines()
        val remoteRoutineIds = remoteRoutines.mapTo(mutableSetOf()) { UUID.fromString(it.id) }

        for (summary in remoteRoutines) {
            val id = UUID.fromString(summary.id)
            val local = routineDao.getById(id)
            if (local != null && local.syncStatus != SyncStatus.SYNCED) {
                skipped++
                continue
            }
            val detail = routineApi.getRoutine(summary.id)
            routineDao.upsert(routineEntity(detail, refreshedAt))
            routineExerciseDao.upsertAll(
                detail.exercises.map { routineExerciseEntity(it, id, refreshedAt) },
            )
            routinesUpdated++
        }

        // A routine the backend no longer lists was deleted elsewhere. Only
        // SYNCED rows are eligible: anything dirty is the outbox's business.
        for (local in routineDao.getAllLive()) {
            if (local.id !in remoteRoutineIds && local.syncStatus == SyncStatus.SYNCED) {
                routineDao.deleteById(local.id)
                routinesRemoved++
            }
        }

        // --- completed history -------------------------------------------
        val remoteSessions = workoutSessionApi.getWorkoutSessions()
        val remoteSessionIds = remoteSessions.mapTo(mutableSetOf()) { UUID.fromString(it.id) }

        for (summary in remoteSessions) {
            val id = UUID.fromString(summary.id)
            val local = workoutSessionDao.getById(id)
            if (local != null && local.syncStatus != SyncStatus.SYNCED) {
                skipped++
                continue
            }
            val detail = workoutSessionApi.getWorkoutSession(summary.id)
            workoutSessionDao.upsert(workoutSessionEntity(detail, refreshedAt))
            for (exercise in detail.exercises) {
                val workoutExerciseId = UUID.fromString(exercise.id)
                workoutExerciseDao.upsert(workoutExerciseEntity(exercise, id, refreshedAt))
                workoutSetDao.upsertAll(
                    exercise.sets.map { workoutSetEntity(it, workoutExerciseId, refreshedAt) },
                )
            }
            sessionsUpdated++
        }

        // Completed sessions only. An in-progress workout is absent from the
        // history endpoint by design, not because it was deleted, and treating
        // that absence as a removal would delete the workout the user is
        // currently performing.
        for (local in workoutSessionDao.getAllCompleted()) {
            if (local.id !in remoteSessionIds && local.syncStatus == SyncStatus.SYNCED) {
                workoutSessionDao.deleteById(local.id)
                sessionsRemoved++
            }
        }

        val outcome = RefreshOutcome.Applied(
            routinesUpdated = routinesUpdated,
            routinesRemoved = routinesRemoved,
            sessionsUpdated = sessionsUpdated,
            sessionsRemoved = sessionsRemoved,
            skippedPendingLocal = skipped,
        )
        if (outcome.changedAnything) {
            Log.i(TAG, "Refresh applied: $outcome")
        }
        return outcome
    }

    private companion object {
        const val TAG = "RefreshManager"
    }
}
