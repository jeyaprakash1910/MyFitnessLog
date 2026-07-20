package com.myfitnesslog.feature.exercise.data

import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository boundary for exercises. Same contract shape as
 * [ExerciseCategoryRepository].
 *
 * Because exercises reference categories by foreign key, callers must refresh
 * categories before exercises (categories are downloaded first). Orchestrating
 * that ordering belongs to the initial-download / sync layer added in a later
 * milestone, not to this repository.
 */
interface ExerciseRepository {

    fun observeAll(): Flow<List<ExerciseEntity>>

    fun observeById(id: UUID): Flow<ExerciseEntity?>

    /**
     * Reactive filtered read over the local Room cache — offline only, never a
     * network call. A null [categoryId] means "any category"; a null/blank
     * [query] means "any name". This single method covers category filtering,
     * name search, and their combination (see [ExerciseDao.observeFiltered]).
     */
    fun observeFiltered(categoryId: UUID?, query: String?): Flow<List<ExerciseEntity>>

    /**
     * Downloads all exercises from the backend and upserts them into Room.
     * Throws on network/parse failure; local data is left untouched.
     */
    suspend fun refresh()
}
