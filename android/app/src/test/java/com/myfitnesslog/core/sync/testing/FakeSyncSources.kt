package com.myfitnesslog.core.sync.testing

import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.sync.source.RoutineExerciseSyncSource
import com.myfitnesslog.core.sync.source.RoutineSyncSource
import com.myfitnesslog.core.sync.source.WorkoutExerciseSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSessionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSetSyncSource
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity
import com.myfitnesslog.feature.workout.data.local.PendingWorkoutSet
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import java.util.UUID

/**
 * In-memory `SyncSource` fakes for engine tests.
 *
 * These live in the **test** source set rather than `core/sync/testing/` in main
 * so no test-only code is compiled into the shipped APK. The package name still
 * matches the agreed structure.
 *
 * Each fake records the full ordered history of status transitions, which is
 * what lets the tests assert the PENDING → SYNCING → SYNCED state machine rather
 * than just its final resting value.
 */

/** One recorded status write, in the order the engine made it. */
data class StatusChange(val id: UUID, val status: SyncStatus)

/** Shared transition recording, so every fake reports transitions identically. */
open class RecordingSyncSource {
    val transitions = mutableListOf<StatusChange>()

    /** How many times recovery was requested, and what it should report. */
    var recoverCallCount = 0
        private set
    var recoverableRows = 0

    fun recover(): Int {
        recoverCallCount++
        return recoverableRows
    }

    protected fun record(id: UUID, status: SyncStatus) {
        transitions += StatusChange(id, status)
    }

    /** The ordered statuses written for one row — the state machine it went through. */
    fun statusesFor(id: UUID): List<SyncStatus> =
        transitions.filter { it.id == id }.map { it.status }
}

class FakeRoutineSyncSource(
    var pending: List<RoutineEntity> = emptyList(),
) : RecordingSyncSource(), RoutineSyncSource {
    var pendingQueryCount = 0
        private set

    override suspend fun getPendingRoutines(): List<RoutineEntity> {
        pendingQueryCount++
        return pending
    }

    override suspend fun setRoutineSyncStatus(id: UUID, status: SyncStatus) = record(id, status)

    override suspend fun recoverStaleSyncing(): Int = recover()
}

class FakeRoutineExerciseSyncSource(
    var pending: List<RoutineExerciseEntity> = emptyList(),
) : RecordingSyncSource(), RoutineExerciseSyncSource {
    override suspend fun getPendingRoutineExercises(): List<RoutineExerciseEntity> = pending

    override suspend fun setRoutineExerciseSyncStatus(id: UUID, status: SyncStatus) =
        record(id, status)

    override suspend fun recoverStaleSyncing(): Int = recover()
}

class FakeWorkoutSessionSyncSource(
    var pending: List<WorkoutSessionEntity> = emptyList(),
) : RecordingSyncSource(), WorkoutSessionSyncSource {
    var pendingQueryCount = 0
        private set

    override suspend fun getPendingWorkoutSessions(): List<WorkoutSessionEntity> {
        pendingQueryCount++
        return pending
    }

    override suspend fun setWorkoutSessionSyncStatus(id: UUID, status: SyncStatus) =
        record(id, status)

    override suspend fun recoverStaleSyncing(): Int = recover()
}

class FakeWorkoutExerciseSyncSource(
    var pending: List<WorkoutExerciseEntity> = emptyList(),
) : RecordingSyncSource(), WorkoutExerciseSyncSource {
    override suspend fun getPendingWorkoutExercises(): List<WorkoutExerciseEntity> = pending

    override suspend fun setWorkoutExerciseSyncStatus(id: UUID, status: SyncStatus) =
        record(id, status)

    override suspend fun recoverStaleSyncing(): Int = recover()
}

class FakeWorkoutSetSyncSource(
    var pending: List<PendingWorkoutSet> = emptyList(),
) : RecordingSyncSource(), WorkoutSetSyncSource {
    override suspend fun getPendingWorkoutSets(): List<PendingWorkoutSet> = pending

    override suspend fun setWorkoutSetSyncStatus(id: UUID, status: SyncStatus) = record(id, status)

    override suspend fun recoverStaleSyncing(): Int = recover()
}
