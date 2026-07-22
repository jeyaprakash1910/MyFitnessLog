package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.myfitnesslog.core.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [WorkoutSetEntity]. Reads are ordered by setNumber.
 *
 * Also owns [WorkoutSetTombstoneEntity], because a tombstone exists only as the
 * shadow of a set deletion: keeping both here lets [deleteAndRecord] remove the
 * row and queue the deletion in one Room transaction, so the two can never
 * diverge. Splitting them across DAOs would need the database handle injected
 * into the repository purely to regain that atomicity.
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

    @Upsert
    suspend fun upsertTombstone(tombstone: WorkoutSetTombstoneEntity)

    /**
     * Deletes a set and queues the deletion for upload, atomically.
     *
     * Both halves must land together. A tombstone without the delete would ask
     * the backend to remove a set the phone still shows; a delete without the
     * tombstone is exactly the bug this table exists to fix.
     */
    @Transaction
    suspend fun deleteAndRecord(tombstone: WorkoutSetTombstoneEntity) {
        deleteById(tombstone.workoutSetId)
        upsertTombstone(tombstone)
    }

    /** Queued deletions, oldest first. Their existence *is* the pending state. */
    @Query("SELECT * FROM workout_set_tombstone ORDER BY deletedAt ASC, workoutSetId ASC")
    suspend fun getPendingTombstones(): List<WorkoutSetTombstoneEntity>

    /** Clears a tombstone once the backend has accepted (or settled) the deletion. */
    @Query("DELETE FROM workout_set_tombstone WHERE workoutSetId = :workoutSetId")
    suspend fun deleteTombstone(workoutSetId: UUID)

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
