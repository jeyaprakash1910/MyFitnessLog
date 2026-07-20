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

    @Query("SELECT * FROM exercise_category ORDER BY name ASC")
    fun observeAll(): Flow<List<ExerciseCategoryEntity>>

    @Query("SELECT * FROM exercise_category WHERE id = :id")
    fun observeById(id: UUID): Flow<ExerciseCategoryEntity?>

    @Upsert
    suspend fun upsert(category: ExerciseCategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<ExerciseCategoryEntity>)
}
