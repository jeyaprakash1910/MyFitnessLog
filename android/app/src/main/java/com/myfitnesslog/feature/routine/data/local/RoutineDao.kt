package com.myfitnesslog.feature.routine.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.myfitnesslog.core.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * DAO for [RoutineEntity].
 *
 * Reads are Flows and exclude soft-deleted rows. Mutations go through [upsert]
 * (insert and update); the repository performs read-modify-upsert for renames,
 * soft-deletes, and reorders so auditing/sync fields stay consistent in one
 * place. [getById] is the non-reactive read the repository uses when
 * duplicating.
 */
@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine WHERE isDeleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<RoutineEntity>>

    /**
     * Routine rows for the list screen, each with its live count of
     * non-deleted exercises via a correlated subquery. Kept as a projection so
     * the Home list can show "N exercises" without loading every exercise row.
     */
    @Query(
        "SELECT r.id AS id, r.name AS name, " +
            "(SELECT COUNT(*) FROM routine_exercise re " +
            "WHERE re.routineId = r.id AND re.isDeleted = 0) AS exerciseCount " +
            "FROM routine r WHERE r.isDeleted = 0 ORDER BY r.name ASC",
    )
    fun observeSummaries(): Flow<List<RoutineSummary>>

    @Query("SELECT * FROM routine WHERE id = :id AND isDeleted = 0")
    fun observeById(id: UUID): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getById(id: UUID): RoutineEntity?

    @Upsert
    suspend fun upsert(routine: RoutineEntity)

    /**
     * Routines awaiting upload, oldest first.
     *
     * PENDING and FAILED are both returned: a FAILED row is simply one whose
     * previous attempt did not succeed and which must be retried. SYNCED and
     * SYNCING rows are excluded — SYNCING means another pass is mid-flight, and
     * claiming it again would double-upload.
     *
     * Soft-deleted routines are **included**. They are how a deletion travels to
     * the backend: the engine inspects `isDeleted` and sends a DELETE instead of
     * a create, so the row that records the deletion is exactly the row that
     * uploads it.
     *
     * This query previously filtered `isDeleted = 0`, on the reasoning that
     * uploading a deleted routine as a create would resurrect it. That is true,
     * but excluding the row did not defer the deletion — it discarded it. The
     * row stayed PENDING forever and the backend never learned of it (M11
     * Phase 1, defect D-1). The fix is to dispatch on `isDeleted`, not to hide
     * the row from the uploader.
     *
     * Ordered by createdAt so parents are uploaded before anything created after
     * them; `id` breaks ties so the order is total and tests are deterministic.
     */
    @Query(
        "SELECT * FROM routine " +
            "WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY createdAt ASC, id ASC",
    )
    suspend fun getPendingSync(): List<RoutineEntity>

    /**
     * Writes **only** the syncStatus column.
     *
     * Deliberately not routed through [upsert]: the repository's write path
     * stamps `updatedAt` from the clock, so recording a successful sync that way
     * would re-dirty the row and make it eligible for upload again — an endless
     * sync loop. `updatedAt` means "when the user last changed this", and a sync
     * is not a user change.
     */
    @Query("UPDATE routine SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: UUID, status: SyncStatus)
    /**
     * Releases rows stranded in SYNCING back to PENDING, returning how many were
     * reclaimed.
     *
     * The engine marks a row SYNCING before uploading it, and the pending query
     * deliberately excludes SYNCING so a concurrent pass cannot double-upload.
     * If the process dies mid-pass, though, nothing ever clears that claim and
     * the row becomes invisible to synchronization forever. Running this before
     * every pass is what makes the claim safe.
     *
     * Writes only syncStatus, and is naturally idempotent: a second run matches
     * no rows and changes nothing.
     */
    @Query("UPDATE routine SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun recoverStaleSyncing(): Int
}
