package com.myfitnesslog.feature.exercise.data

import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.exercise.data.local.ExerciseDao
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.exercise.data.remote.ExerciseApi
import com.myfitnesslog.feature.exercise.data.remote.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Default [ExerciseRepository]. Mirrors [ExerciseCategoryRepositoryImpl].
 */
class ExerciseRepositoryImpl @Inject constructor(
    private val dao: ExerciseDao,
    private val api: ExerciseApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExerciseRepository {

    override fun observeAll(): Flow<List<ExerciseEntity>> = dao.observeAll()

    override fun observeById(id: UUID): Flow<ExerciseEntity?> = dao.observeById(id)

    override fun observeFiltered(categoryId: UUID?, query: String?): Flow<List<ExerciseEntity>> =
        dao.observeFiltered(categoryId = categoryId, query = query)

    override suspend fun refresh() = withContext(ioDispatcher) {
        val exercises = api.getExercises().map { it.toEntity() }
        dao.upsertAll(exercises)
    }
}
