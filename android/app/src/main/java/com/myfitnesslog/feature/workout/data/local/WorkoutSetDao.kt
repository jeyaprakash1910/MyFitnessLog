package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfitnesslog.core.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [WorkoutSetEntity]. Reads are ordered by setNumber. `deleteById`
 * supports the "delete set" action during an in-progress workout.
 */
@Dao
interface WorkoutSetDao {

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber ASC")
    fun observeByExercise(workoutExerciseId: UUID): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber ASC")
    suspend fun getByExercise(workoutExerciseId: UUID): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getById(id: UUID): WorkoutSetEntity?

    @Upsert
    suspend fun upsert(set: WorkoutSetEntity)

    @Upsert
    suspend fun upsertAll(sets: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteById(id: UUID)
    /**
     * Sets awaiting upload, joined to their grandparent session id.
     *
     * A set row holds only its workoutExerciseId, but the engine needs to know
     * which *session* a set belongs to: if a session or one of its exercises
     * failed to upload, its sets must be skipped rather than attempted against a
     * parent the backend does not have. Resolving that with a join here is one
     * query instead of one lookup per set.
     *
     * Ordered by session, then position, then set number — the order the sets
     * were actually performed in.
     */
    @Query(
        """
        SELECT ws.*, we.workoutSessionId AS workoutSessionId
        FROM workout_set ws
        INNER JOIN workout_exercise we ON we.id = ws.workoutExerciseId
        WHERE ws.syncStatus IN ('PENDING', 'FAILED')
        ORDER BY we.workoutSessionId ASC, we.exerciseOrder ASC, ws.setNumber ASC, ws.id ASC
        """,
    )
    suspend fun getPendingSync(): List<PendingWorkoutSet>

    /** Writes only syncStatus — never updatedAt. See [RoutineDao.updateSyncStatus]. */
    @Query("UPDATE workout_set SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: UUID, status: SyncStatus)
    /** Releases stranded SYNCING rows back to PENDING. See [RoutineDao.recoverStaleSyncing]. */
    @Query("UPDATE workout_set SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun recoverStaleSyncing(): Int
}
