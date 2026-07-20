package com.myfitnesslog.feature.exercise.data

import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository boundary for exercise categories.
 *
 * The repository is the only component that knows both Room and Retrofit exist
 * (ADR-0002). Reads are Room-backed [Flow]s (the UI observes Room); [refresh]
 * downloads from the backend and upserts into Room.
 *
 * Entities are exposed directly as the domain model for Version 1, per the
 * approved architecture (docs/ANDROID_ARCHITECTURE.md, section 3). The interface
 * lets ViewModels and tests depend on an abstraction rather than the impl.
 */
interface ExerciseCategoryRepository {

    fun observeAll(): Flow<List<ExerciseCategoryEntity>>

    fun observeById(id: UUID): Flow<ExerciseCategoryEntity?>

    /**
     * Downloads all categories from the backend and upserts them into Room.
     * Throws on network/parse failure; local data is left untouched because the
     * upsert only runs after a successful response.
     */
    suspend fun refresh()
}
