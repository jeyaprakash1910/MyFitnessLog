package com.myfitnesslog.feature.routine.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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

    @Query("SELECT * FROM routine WHERE id = :id AND isDeleted = 0")
    fun observeById(id: UUID): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getById(id: UUID): RoutineEntity?

    @Upsert
    suspend fun upsert(routine: RoutineEntity)
}
