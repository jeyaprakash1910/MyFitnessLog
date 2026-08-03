package com.myfitnesslog.feature.routine.data

import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDetail
import com.myfitnesslog.feature.routine.data.local.RoutineSummary
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository boundary for routine templates — the first mutable repository.
 *
 * Convention for mutable repositories (docs/ANDROID_ARCHITECTURE.md §6a):
 *  - observe*  : Room-backed [Flow]s the UI collects (source of truth).
 *  - write ops : suspend functions that persist locally and mark affected rows
 *                as pending synchronization; they never call the network.
 *
 * Everything here is local-only (offline-first). Uploading these changes to the
 * backend is the synchronization layer's job (Milestone 9); write operations
 * simply leave records with a PENDING sync status for it to pick up.
 */
interface RoutineRepository {

    fun observeRoutines(): Flow<List<RoutineEntity>>

    /** Routine rows plus their exercise counts, for the Home list. */
    fun observeRoutineSummaries(): Flow<List<RoutineSummary>>

    fun observeRoutine(id: UUID): Flow<RoutineEntity?>

    fun observeRoutineExercises(routineId: UUID): Flow<List<RoutineExerciseDetail>>

    /**
     * One-shot read of a routine's exercises (with names), used by the workout
     * layer to snapshot a routine when a workout starts.
     */
    suspend fun getRoutineExerciseDetails(routineId: UUID): List<RoutineExerciseDetail>

    /** Creates a routine and returns its new id. */
    suspend fun createRoutine(name: String): UUID

    suspend fun renameRoutine(id: UUID, name: String)

    /** Soft-deletes the routine (its history is unaffected). */
    suspend fun deleteRoutine(id: UUID)

    /** Deep-copies a routine and its exercises; returns the new routine id. */
    suspend fun duplicateRoutine(id: UUID): UUID

    /** Appends an exercise to the routine and returns the new row id. */
    suspend fun addExercise(
        routineId: UUID,
        exerciseId: UUID,
        targetSets: Int,
        minTargetReps: Int,
        maxTargetReps: Int,
        targetRestSeconds: Int?,
        notes: String?,
    ): UUID

    suspend fun updateExercise(
        routineExerciseId: UUID,
        targetSets: Int,
        minTargetReps: Int,
        maxTargetReps: Int,
        targetRestSeconds: Int?,
        notes: String?,
    )

    /** Soft-removes an exercise from its routine. */
    suspend fun removeExercise(routineExerciseId: UUID)

    /** Re-sequences the routine's exercises to match [orderedIds]. */
    suspend fun reorderExercises(routineId: UUID, orderedIds: List<UUID>)
}
