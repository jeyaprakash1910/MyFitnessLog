package com.myfitnesslog.feature.exercise.data

import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.remote.ExerciseCategoryApi
import com.myfitnesslog.feature.exercise.data.remote.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Default [ExerciseCategoryRepository]: reads from the [ExerciseCategoryDao] and
 * refreshes from the [ExerciseCategoryApi], mapping DTOs to entities. The
 * injected IO dispatcher keeps the network + DB work off the caller's thread and
 * lets tests substitute a test dispatcher.
 */
class ExerciseCategoryRepositoryImpl @Inject constructor(
    private val dao: ExerciseCategoryDao,
    private val api: ExerciseCategoryApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExerciseCategoryRepository {

    override fun observeAll(): Flow<List<ExerciseCategoryEntity>> = dao.observeAll()

    override fun observeById(id: UUID): Flow<ExerciseCategoryEntity?> = dao.observeById(id)

    override suspend fun refresh() = withContext(ioDispatcher) {
        val categories = api.getCategories().map { it.toEntity() }
        dao.upsertAll(categories)
    }
}
