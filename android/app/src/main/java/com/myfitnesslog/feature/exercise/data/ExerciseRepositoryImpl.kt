package com.myfitnesslog.feature.exercise.data

import androidx.room.withTransaction
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryDao
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
    private val categoryDao: ExerciseCategoryDao,
    private val database: MyFitnessLogDatabase,
    private val api: ExerciseApi,
    private val categoryRepository: ExerciseCategoryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExerciseRepository {

    override fun observeAll(): Flow<List<ExerciseEntity>> = dao.observeAll()

    override fun observeById(id: UUID): Flow<ExerciseEntity?> = dao.observeById(id)

    override fun observeFiltered(categoryId: UUID?, query: String?): Flow<List<ExerciseEntity>> =
        dao.observeFiltered(categoryId = categoryId, query = query)

    /**
     * Replaces the local catalogue with the backend's.
     *
     * Reference data is server-authoritative (ADR-0003), so this is a
     * reconciliation, not an append: rows are added, updated **and removed** so
     * the device converges on what the server actually serves. Previously it
     * only upserted, so a withdrawn exercise or category stayed on the device
     * forever (M11 Phase 1, defect D-3).
     *
     * Both fetches happen before the transaction opens: a network call inside a
     * database transaction would hold it open for the duration of the request.
     * If either fetch fails nothing is written, so a failed refresh leaves the
     * previous catalogue intact rather than half-replacing it.
     *
     * Ordering inside the transaction is dictated by the RESTRICT foreign keys —
     * insert parents first, delete children first:
     *
     * ```
     * upsert categories → upsert exercises → delete exercises → delete categories
     * ```
     */
    override suspend fun refreshLibrary() = withContext(ioDispatcher) {
        val categories = categoryRepository.fetch()
        val exercises = api.getExercises().map { it.toEntity() }

        // An empty catalogue is not a credible server state — the library is
        // seeded by Flyway and has never legitimately been empty — so an empty
        // response almost certainly means a misconfigured or half-deployed
        // backend. Reconciling against it would delete every unreferenced row
        // and leave the picker blank, which is precisely the failure that made
        // the app unusable before M9.5 T2. Upserts still apply (there is nothing
        // to apply); only the destructive half is skipped, and the next healthy
        // response reconciles normally.
        //
        // This is not a departure from ADR-0003: the backend remains the source
        // of truth. It is a refusal to destroy local data on a response that
        // cannot be true.
        val catalogueIsCredible = exercises.isNotEmpty() && categories.isNotEmpty()

        database.withTransaction {
            categoryDao.upsertAll(categories)
            dao.upsertAll(exercises)
            if (catalogueIsCredible) {
                dao.deleteMissing(exercises.map { it.id })
                categoryDao.deleteMissing(categories.map { it.id })
            }
        }
        Unit
    }
}
