package com.myfitnesslog.feature.exercise.data

import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository boundary for exercises. Same contract shape as
 * [ExerciseCategoryRepository].
 *
 * Exercises reference categories by a RESTRICT foreign key, so categories must
 * be downloaded first or the upsert fails. That ordering is owned here, by
 * [refreshLibrary] — deliberately not left to callers. An earlier design left it
 * to "the initial-download layer", and the result was that the one caller which
 * existed refreshed exercises alone and would have failed on a cold database.
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
     * Downloads the exercise library — categories **then** exercises — and
     * upserts it into Room.
     *
     * This is the only refresh entry point precisely so that the foreign-key
     * ordering cannot be got wrong: there is no way to refresh exercises without
     * their categories.
     *
     * Throws on network/parse failure. Local data is left untouched, so a failed
     * refresh degrades to whatever was already cached rather than emptying the
     * library.
     */
    suspend fun refreshLibrary()
}
