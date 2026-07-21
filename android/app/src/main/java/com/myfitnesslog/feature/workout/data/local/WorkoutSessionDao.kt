package com.myfitnesslog.feature.workout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfitnesslog.core.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [WorkoutSessionEntity].
 *
 * `observeActive`/`getActive` support the single-active-session invariant
 * (enforced in the repository in Phase 2). `deleteById` exists to purge a
 * discarded/abandoned session and its children via cascade (DATABASE.md permits
 * deleting discarded workouts); completed history is never deleted.
 */
@Dao
interface WorkoutSessionDao {

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeById(id: UUID): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getById(id: UUID): WorkoutSessionEntity?

    @Upsert
    suspend fun upsert(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteById(id: UUID)
    /**
     * Workout sessions awaiting upload, oldest first.
     *
     * There is no soft-delete filter because history rows are never soft-deleted.
     * Ordered by startedAt — the chronological order in which the workouts
     * actually happened, and the column the table is indexed on.
     */
    @Query(
        "SELECT * FROM workout_session WHERE syncStatus IN ('PENDING', 'FAILED') " +
            "ORDER BY startedAt ASC, id ASC",
    )
    suspend fun getPendingSync(): List<WorkoutSessionEntity>

    /** Writes only syncStatus — never updatedAt. See [RoutineDao.updateSyncStatus]. */
    @Query("UPDATE workout_session SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: UUID, status: SyncStatus)
    /** Releases stranded SYNCING rows back to PENDING. See [RoutineDao.recoverStaleSyncing]. */
    @Query("UPDATE workout_session SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun recoverStaleSyncing(): Int
}
