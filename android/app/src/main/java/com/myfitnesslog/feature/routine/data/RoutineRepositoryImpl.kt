package com.myfitnesslog.feature.routine.data

import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.routine.data.local.RoutineDao
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDao
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDetail
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Default [RoutineRepository]. All writes run on the injected IO dispatcher,
 * stamp updatedAt from the injected [Clock], and mark affected rows PENDING so
 * the future sync layer can upload them. New identifiers are UUIDv4 generated
 * on-device (DATABASE.md).
 */
class RoutineRepositoryImpl @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : RoutineRepository {

    override fun observeRoutines(): Flow<List<RoutineEntity>> = routineDao.observeAll()

    override fun observeRoutine(id: UUID): Flow<RoutineEntity?> = routineDao.observeById(id)

    override fun observeRoutineExercises(routineId: UUID): Flow<List<RoutineExerciseDetail>> =
        routineExerciseDao.observeDetailsByRoutine(routineId)

    override suspend fun getRoutineExerciseDetails(routineId: UUID): List<RoutineExerciseDetail> =
        withContext(ioDispatcher) { routineExerciseDao.getDetailsByRoutine(routineId) }

    override suspend fun createRoutine(name: String): UUID = withContext(ioDispatcher) {
        val now = clock.instant()
        val id = UUID.randomUUID()
        routineDao.upsert(
            RoutineEntity(
                id = id,
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        id
    }

    override suspend fun renameRoutine(id: UUID, name: String) = withContext(ioDispatcher) {
        val existing = routineDao.getById(id) ?: return@withContext
        routineDao.upsert(
            existing.copy(
                name = name.trim(),
                updatedAt = clock.instant(),
                syncStatus = SyncStatus.PENDING,
            ),
        )
    }

    override suspend fun deleteRoutine(id: UUID) = withContext(ioDispatcher) {
        val existing = routineDao.getById(id) ?: return@withContext
        routineDao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.instant(),
                syncStatus = SyncStatus.PENDING,
            ),
        )
    }

    override suspend fun duplicateRoutine(id: UUID): UUID = withContext(ioDispatcher) {
        val source = routineDao.getById(id) ?: return@withContext id
        val now = clock.instant()
        val newRoutineId = UUID.randomUUID()
        routineDao.upsert(
            source.copy(
                id = newRoutineId,
                name = "${source.name} (copy)",
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        val copies = routineExerciseDao.getByRoutine(id).map { original ->
            original.copy(
                id = UUID.randomUUID(),
                routineId = newRoutineId,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
            )
        }
        if (copies.isNotEmpty()) routineExerciseDao.upsertAll(copies)
        newRoutineId
    }

    override suspend fun addExercise(
        routineId: UUID,
        exerciseId: UUID,
        targetSets: Int,
        minTargetReps: Int,
        maxTargetReps: Int,
        targetRestSeconds: Int?,
        notes: String?,
    ): UUID = withContext(ioDispatcher) {
        val now = clock.instant()
        val nextOrder = (routineExerciseDao.getByRoutine(routineId).maxOfOrNull { it.displayOrder } ?: -1) + 1
        val id = UUID.randomUUID()
        routineExerciseDao.upsert(
            RoutineExerciseEntity(
                id = id,
                routineId = routineId,
                exerciseId = exerciseId,
                displayOrder = nextOrder,
                targetSets = targetSets,
                minTargetReps = minTargetReps,
                maxTargetReps = maxTargetReps,
                targetRestSeconds = targetRestSeconds,
                notes = notes?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        id
    }

    override suspend fun updateExercise(
        routineExerciseId: UUID,
        targetSets: Int,
        minTargetReps: Int,
        maxTargetReps: Int,
        targetRestSeconds: Int?,
        notes: String?,
    ) = withContext(ioDispatcher) {
        val existing = routineExerciseDao.getById(routineExerciseId) ?: return@withContext
        routineExerciseDao.upsert(
            existing.copy(
                targetSets = targetSets,
                minTargetReps = minTargetReps,
                maxTargetReps = maxTargetReps,
                targetRestSeconds = targetRestSeconds,
                notes = notes?.trim()?.ifBlank { null },
                updatedAt = clock.instant(),
                syncStatus = SyncStatus.PENDING,
            ),
        )
    }

    override suspend fun removeExercise(routineExerciseId: UUID) = withContext(ioDispatcher) {
        val existing = routineExerciseDao.getById(routineExerciseId) ?: return@withContext
        routineExerciseDao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.instant(),
                syncStatus = SyncStatus.PENDING,
            ),
        )
    }

    override suspend fun reorderExercises(routineId: UUID, orderedIds: List<UUID>) =
        withContext(ioDispatcher) {
            val now: Instant = clock.instant()
            val byId = routineExerciseDao.getByRoutine(routineId).associateBy { it.id }
            val reordered = orderedIds.mapIndexedNotNull { index, id ->
                byId[id]?.takeIf { it.displayOrder != index }?.copy(
                    displayOrder = index,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                )
            }
            if (reordered.isNotEmpty()) routineExerciseDao.upsertAll(reordered)
        }
}
