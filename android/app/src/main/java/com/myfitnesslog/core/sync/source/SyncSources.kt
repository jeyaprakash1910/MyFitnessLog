package com.myfitnesslog.core.sync.source

import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity
import com.myfitnesslog.feature.workout.data.local.PendingWorkoutSet
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import java.util.UUID

/**
 * The persistence surface the synchronization engine is allowed to touch.
 *
 * These interfaces exist so the engine never depends on Room: it asks for
 * pending work and records status transitions, and the existing repositories
 * implement them on top of their DAOs. Persistence logic therefore stays in one
 * place (the repository) while the UI-facing repository interfaces stay free of
 * synchronization concerns that no screen has any use for.
 *
 * Each entity gets its own interface rather than one wide `SyncSource`, so the
 * engine's dependency on each aggregate is explicit and a test can fake exactly
 * the part it exercises.
 *
 * Every `setXSyncStatus` implementation must write **only** the syncStatus
 * column: touching `updatedAt` would re-dirty the row and loop forever.
 */

interface RoutineSyncSource {
    /** Routines needing upload (PENDING or FAILED), in dependency order. */
    suspend fun getPendingRoutines(): List<RoutineEntity>

    suspend fun setRoutineSyncStatus(id: UUID, status: SyncStatus)

    /** Releases rows stranded in SYNCING back to PENDING; returns the count. */
    suspend fun recoverStaleSyncing(): Int
}

interface RoutineExerciseSyncSource {
    suspend fun getPendingRoutineExercises(): List<RoutineExerciseEntity>

    suspend fun setRoutineExerciseSyncStatus(id: UUID, status: SyncStatus)

    /** Releases rows stranded in SYNCING back to PENDING; returns the count. */
    suspend fun recoverStaleSyncing(): Int
}

interface WorkoutSessionSyncSource {
    suspend fun getPendingWorkoutSessions(): List<WorkoutSessionEntity>

    suspend fun setWorkoutSessionSyncStatus(id: UUID, status: SyncStatus)

    /** Releases rows stranded in SYNCING back to PENDING; returns the count. */
    suspend fun recoverStaleSyncing(): Int
}

interface WorkoutExerciseSyncSource {
    suspend fun getPendingWorkoutExercises(): List<WorkoutExerciseEntity>

    suspend fun setWorkoutExerciseSyncStatus(id: UUID, status: SyncStatus)

    /** Releases rows stranded in SYNCING back to PENDING; returns the count. */
    suspend fun recoverStaleSyncing(): Int
}

interface WorkoutSetSyncSource {
    /** Pending sets, each carrying the id of the session it belongs to. */
    suspend fun getPendingWorkoutSets(): List<PendingWorkoutSet>

    suspend fun setWorkoutSetSyncStatus(id: UUID, status: SyncStatus)

    /** Releases rows stranded in SYNCING back to PENDING; returns the count. */
    suspend fun recoverStaleSyncing(): Int
}
