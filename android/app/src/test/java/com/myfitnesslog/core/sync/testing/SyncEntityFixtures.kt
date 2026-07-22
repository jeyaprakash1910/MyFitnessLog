package com.myfitnesslog.core.sync.testing

import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity
import com.myfitnesslog.feature.workout.data.local.PendingWorkoutSet
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Entity builders for synchronization tests, with every field defaulted so a
 * test only states what it actually cares about.
 */
object SyncEntityFixtures {

    val TIME: Instant = Instant.parse("2026-07-21T09:00:00Z")
    val END_TIME: Instant = Instant.parse("2026-07-21T10:30:00Z")

    fun routine(
        id: UUID = UUID.randomUUID(),
        name: String = "Push Day",
        syncStatus: SyncStatus = SyncStatus.PENDING,
        isDeleted: Boolean = false,
    ) = RoutineEntity(
        id = id,
        name = name,
        createdAt = TIME,
        updatedAt = TIME,
        isDeleted = isDeleted,
        syncStatus = syncStatus,
    )

    fun routineExercise(
        id: UUID = UUID.randomUUID(),
        routineId: UUID,
        exerciseId: UUID = UUID.randomUUID(),
        displayOrder: Int = 0,
    ) = RoutineExerciseEntity(
        id = id,
        routineId = routineId,
        exerciseId = exerciseId,
        displayOrder = displayOrder,
        targetSets = 3,
        minTargetReps = 8,
        maxTargetReps = 12,
        targetRestSeconds = 90,
        notes = null,
        createdAt = TIME,
        updatedAt = TIME,
    )

    fun session(
        id: UUID = UUID.randomUUID(),
        routineId: UUID? = UUID.randomUUID(),
        status: WorkoutStatus = WorkoutStatus.COMPLETED,
        endedAt: Instant? = END_TIME,
    ) = WorkoutSessionEntity(
        id = id,
        routineId = routineId,
        status = status,
        startedAt = TIME,
        endedAt = endedAt,
        notes = null,
        createdAt = TIME,
        updatedAt = TIME,
    )

    fun workoutExercise(
        id: UUID = UUID.randomUUID(),
        sessionId: UUID,
        exerciseOrder: Int = 0,
    ) = WorkoutExerciseEntity(
        id = id,
        workoutSessionId = sessionId,
        exerciseId = UUID.randomUUID(),
        exerciseName = "Bench Press",
        exerciseOrder = exerciseOrder,
        targetSets = 3,
        minTargetReps = 8,
        maxTargetReps = 12,
        targetRestSeconds = 90,
        notes = null,
        createdAt = TIME,
        updatedAt = TIME,
    )

    fun pendingSet(
        id: UUID = UUID.randomUUID(),
        workoutExerciseId: UUID,
        sessionId: UUID,
        setNumber: Int = 1,
    ) = PendingWorkoutSet(
        set = com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity(
            id = id,
            workoutExerciseId = workoutExerciseId,
            setNumber = setNumber,
            weight = BigDecimal("60.00"),
            repetitions = 10,
            setCategory = SetCategory.WORKING,
            startedAt = TIME,
            finishedAt = null,
            rpe = null,
            rir = null,
            isCompleted = true,
            createdAt = TIME,
            updatedAt = TIME,
        ),
        workoutSessionId = sessionId,
    )

    fun setTombstone(
        workoutSetId: UUID = UUID.randomUUID(),
        workoutExerciseId: UUID = UUID.randomUUID(),
        sessionId: UUID,
        deletedAt: Instant = TIME,
    ) = com.myfitnesslog.feature.workout.data.local.WorkoutSetTombstoneEntity(
        workoutSetId = workoutSetId,
        workoutExerciseId = workoutExerciseId,
        workoutSessionId = sessionId,
        deletedAt = deletedAt,
    )
}
