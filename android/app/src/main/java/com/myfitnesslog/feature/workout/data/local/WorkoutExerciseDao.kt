package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    // --- deletion tombstones (TD-014) ------------------------------------
    //
    // Owned here for the same reason WorkoutSetDao owns its tombstones: the
    // tombstone exists only as the shadow of a deletion, and keeping both in one
    // DAO lets the removal and the queued deletion happen in a single Room
    // transaction so they can never diverge.

    @Upsert
    suspend fun upsertTombstone(tombstone: WorkoutExerciseTombstoneEntity)

    /**
     * Removes an exercise and queues its deletion for upload, atomically.
     *
     * The two halves must not be separable. A tombstone without the delete would
     * ask the backend to remove an exercise the phone still shows; a delete
     * without the tombstone is exactly the divergence TD-014 was filed for.
     */
    @Transaction
    suspend fun deleteAndRecord(tombstone: WorkoutExerciseTombstoneEntity) {
        deleteById(tombstone.workoutExerciseId)
        upsertTombstone(tombstone)
    }

    @Query("SELECT * FROM workout_exercise_tombstone ORDER BY deletedAt ASC")
    suspend fun getPendingTombstones(): List<WorkoutExerciseTombstoneEntity>

    @Query("DELETE FROM workout_exercise_tombstone WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun deleteTombstone(workoutExerciseId: UUID)
}
