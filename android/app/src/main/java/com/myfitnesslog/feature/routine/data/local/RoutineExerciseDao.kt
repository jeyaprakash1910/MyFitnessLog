package com.myfitnesslog.feature.routine.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfitnesslog.core.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [RoutineExerciseEntity].
 *
 * The reactive read joins the master exercise name for display
 * ([RoutineExerciseDetail]) and excludes soft-deleted rows, ordered by
 * displayOrder. [getByRoutine] is the non-reactive read the repository uses when
 * reordering and duplicating. Mutations go through [upsert]/[upsertAll].
 */
@Dao
interface RoutineExerciseDao {

    @Query(
        """
        SELECT re.*, e.name AS exerciseName
        FROM routine_exercise re
        JOIN exercise e ON e.id = re.exerciseId
        WHERE re.routineId = :routineId AND re.isDeleted = 0
        ORDER BY re.displayOrder ASC
        """,
    )
    fun observeDetailsByRoutine(routineId: UUID): Flow<List<RoutineExerciseDetail>>

    @Query(
        "SELECT * FROM routine_exercise WHERE routineId = :routineId AND isDeleted = 0 " +
            "ORDER BY displayOrder ASC",
    )
    suspend fun getByRoutine(routineId: UUID): List<RoutineExerciseEntity>

    /**
     * Non-reactive detail read (with exercise name) used when snapshotting a
     * routine into a workout at start (Milestone 6).
     */
    @Query(
        """
        SELECT re.*, e.name AS exerciseName
        FROM routine_exercise re
        JOIN exercise e ON e.id = re.exerciseId
        WHERE re.routineId = :routineId AND re.isDeleted = 0
        ORDER BY re.displayOrder ASC
        """,
    )
    suspend fun getDetailsByRoutine(routineId: UUID): List<RoutineExerciseDetail>

    @Query("SELECT * FROM routine_exercise WHERE id = :id")
    suspend fun getById(id: UUID): RoutineExerciseEntity?

    @Upsert
    suspend fun upsert(routineExercise: RoutineExerciseEntity)

    @Upsert
    suspend fun upsertAll(routineExercises: List<RoutineExerciseEntity>)
    /**
     * Routine exercises awaiting upload, oldest first. Same PENDING/FAILED rule
     * as [RoutineDao.getPendingSync], and — as there — soft-deleted rows are
     * **included** so their deletion can be uploaded (M11 Phase 1, defect D-1).
     *
     * The parent routine is not joined here: the engine uploads routines in an
     * earlier phase and already knows which of them failed or were deleted, so it
     * can skip the children of such a parent without a second query.
     */
    @Query(
        "SELECT * FROM routine_exercise " +
            "WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY createdAt ASC, id ASC",
    )
    suspend fun getPendingSync(): List<RoutineExerciseEntity>

    /** Writes only syncStatus — never updatedAt. See [RoutineDao.updateSyncStatus]. */
    @Query("UPDATE routine_exercise SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: UUID, status: SyncStatus)
    /** Releases stranded SYNCING rows back to PENDING. See [RoutineDao.recoverStaleSyncing]. */
    @Query("UPDATE routine_exercise SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun recoverStaleSyncing(): Int
}
