package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfitnesslog.core.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [WorkoutExerciseEntity]. Reads are ordered by exerciseOrder. No join
 * for the exercise name is needed — it is snapshotted onto the row.
 */
@Dao
interface WorkoutExerciseDao {

    @Query("SELECT * FROM workout_exercise WHERE workoutSessionId = :sessionId ORDER BY exerciseOrder ASC")
    fun observeBySession(sessionId: UUID): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercise WHERE workoutSessionId = :sessionId ORDER BY exerciseOrder ASC")
    suspend fun getBySession(sessionId: UUID): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercise WHERE id = :id")
    suspend fun getById(id: UUID): WorkoutExerciseEntity?

    @Upsert
    suspend fun upsert(workoutExercise: WorkoutExerciseEntity)

    @Upsert
    suspend fun upsertAll(workoutExercises: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteById(id: UUID)
    /**
     * Workout exercises awaiting upload, ordered by session then position within
     * that session, so a session's exercises upload in the order they were
     * performed.
     */
    @Query(
        "SELECT * FROM workout_exercise WHERE syncStatus IN ('PENDING', 'FAILED') " +
            "ORDER BY workoutSessionId ASC, exerciseOrder ASC, id ASC",
    )
    suspend fun getPendingSync(): List<WorkoutExerciseEntity>

    /** Writes only syncStatus — never updatedAt. See [RoutineDao.updateSyncStatus]. */
    @Query("UPDATE workout_exercise SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: UUID, status: SyncStatus)
    /** Releases stranded SYNCING rows back to PENDING. See [RoutineDao.recoverStaleSyncing]. */
    @Query("UPDATE workout_exercise SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun recoverStaleSyncing(): Int
}
