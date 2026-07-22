package com.myfitnesslog.feature.exercise.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data-access object for [ExerciseCategoryEntity].
 *
 * Reads are exposed as [Flow] so the UI observes Room and re-renders reactively
 * (ADR-0002). Writes are `suspend`. Only the operations the app needs today are
 * declared: reactive reads and idempotent upserts used when refreshing the
 * download-only cache by UUID. No search or delete methods are added yet
 * (YAGNI) — search is served by the backend at Milestone 4.
 */
@Dao
interface ExerciseCategoryDao {

    /**
     * Categories in the backend's curated order, with `name` as a deterministic
     * tiebreak so equal positions still sort stably (and so a response from a
     * backend that omits `displayOrder` degrades to the previous alphabetical
     * behaviour rather than an arbitrary one).
     */
    @Query("SELECT * FROM exercise_category ORDER BY displayOrder ASC, name ASC")
    fun observeAll(): Flow<List<ExerciseCategoryEntity>>

    @Query("SELECT * FROM exercise_category WHERE id = :id")
    fun observeById(id: UUID): Flow<ExerciseCategoryEntity?>

    @Upsert
    suspend fun upsert(category: ExerciseCategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<ExerciseCategoryEntity>)

    /**
     * Removes categories the backend no longer serves.
     *
     * Must run *after* [ExerciseDao.deleteMissing]: `Exercise` has a RESTRICT
     * foreign key to this table, so a category is only removable once the
     * exercises pointing at it have gone. Any category still referenced is kept,
     * for the same reason exercises are (see that method).
     */
    @Query(
        """
        DELETE FROM exercise_category
        WHERE id NOT IN (:serverIds)
          AND id NOT IN (SELECT categoryId FROM exercise)
        """,
    )
    suspend fun deleteMissing(serverIds: List<UUID>): Int
}
