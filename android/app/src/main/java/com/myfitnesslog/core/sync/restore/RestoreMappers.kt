package com.myfitnesslog.core.sync.restore

import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity
import com.myfitnesslog.feature.routine.data.remote.RoutineDetailResponseDto
import com.myfitnesslog.feature.routine.data.remote.RoutineExerciseResponseDto
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import com.myfitnesslog.feature.workout.data.remote.WorkoutExerciseDetailResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionDetailResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutSetResponseDto
import java.time.Instant
import java.util.UUID

/**
 * Translates backend responses into Room entities: the download direction, which
 * until now did not exist anywhere in the app (ADR-0017).
 *
 * These are deliberately *not* the inverse of the upload mappers, and must not be
 * assumed symmetric with them. Three differences are structural rather than
 * incidental, and each is a decision rather than a shortcut:
 *
 * 1. **Everything is written as [SyncStatus.SYNCED].** These rows came *from* the
 *    backend, so the phone owes it nothing for them. Writing them as PENDING (the
 *    entity default) would make restore immediately re-upload the entire history
 *    it just downloaded.
 *
 * 2. **`createdAt` and `updatedAt` come from the server when it sends them**, and
 *    fall back to the restore instant when it does not. The backend began
 *    returning both on 2026-08-08; before that, restored rows were all stamped
 *    with the moment of the restore, losing their real history. The fallback is
 *    not defensive clutter: the phone talks to whatever backend is deployed, and
 *    a build newer than the server must still restore rather than crash.
 *
 *    These are the values ADR-0017 Stage 3 arbitrates last-write-wins on, and
 *    they are the *server's*, never the device's. Nothing compares them yet.
 *
 * 3. **Unknown enum values fall back rather than throwing.** A newer backend may
 *    send a status or set category this build has never heard of, and one
 *    unrecognised word must not abort the restore of a decade of training.
 */

internal fun routineEntity(dto: RoutineDetailResponseDto, restoredAt: Instant) =
    RoutineEntity(
        id = UUID.fromString(dto.id),
        name = dto.name,
        // description and displayOrder exist on the backend but not in Room
        // (TD-009), so they are dropped rather than silently invented here.
        createdAt = dto.createdAt ?: restoredAt,
        updatedAt = dto.updatedAt ?: restoredAt,
        isDeleted = false,
        syncStatus = SyncStatus.SYNCED,
    )

internal fun routineExerciseEntity(
    dto: RoutineExerciseResponseDto,
    routineId: UUID,
    restoredAt: Instant,
) = RoutineExerciseEntity(
    id = UUID.fromString(dto.id),
    routineId = routineId,
    exerciseId = UUID.fromString(dto.exerciseId),
    displayOrder = dto.exerciseOrder,
    targetSets = dto.targetSets,
    minTargetReps = dto.minTargetReps,
    maxTargetReps = dto.maxTargetReps,
    targetRestSeconds = dto.targetRestSeconds,
    notes = dto.notes,
    createdAt = dto.createdAt ?: restoredAt,
    updatedAt = dto.updatedAt ?: restoredAt,
    isDeleted = false,
    syncStatus = SyncStatus.SYNCED,
)

internal fun workoutSessionEntity(dto: WorkoutSessionDetailResponseDto, restoredAt: Instant) =
    WorkoutSessionEntity(
        id = UUID.fromString(dto.id),
        routineId = dto.routineId?.let(UUID::fromString),
        status = dto.status.toWorkoutStatus(),
        startedAt = dto.startedAt,
        endedAt = dto.endedAt,
        notes = dto.notes,
        createdAt = dto.createdAt ?: restoredAt,
        updatedAt = dto.updatedAt ?: restoredAt,
        syncStatus = SyncStatus.SYNCED,
    )

internal fun workoutExerciseEntity(
    dto: WorkoutExerciseDetailResponseDto,
    sessionId: UUID,
    restoredAt: Instant,
) = WorkoutExerciseEntity(
    id = UUID.fromString(dto.id),
    workoutSessionId = sessionId,
    exerciseId = UUID.fromString(dto.exerciseId),
    exerciseName = dto.exerciseName,
    exerciseOrder = dto.exerciseOrder,
    targetSets = dto.targetSets,
    minTargetReps = dto.minTargetReps,
    maxTargetReps = dto.maxTargetReps,
    targetRestSeconds = dto.targetRestSeconds,
    notes = dto.notes,
    createdAt = dto.createdAt ?: restoredAt,
    updatedAt = dto.updatedAt ?: restoredAt,
    syncStatus = SyncStatus.SYNCED,
)

internal fun workoutSetEntity(
    dto: WorkoutSetResponseDto,
    workoutExerciseId: UUID,
    restoredAt: Instant,
) = WorkoutSetEntity(
    id = UUID.fromString(dto.id),
    workoutExerciseId = workoutExerciseId,
    setNumber = dto.setNumber,
    weight = dto.weight,
    repetitions = dto.repetitions,
    setCategory = dto.setCategory.toSetCategory(),
    startedAt = dto.startedAt,
    finishedAt = dto.finishedAt,
    rpe = dto.rpe,
    rir = dto.rir,
    isCompleted = dto.isCompleted,
    createdAt = dto.createdAt ?: restoredAt,
    updatedAt = dto.updatedAt ?: restoredAt,
    syncStatus = SyncStatus.SYNCED,
)

/**
 * Falls back to [WorkoutStatus.COMPLETED] for anything unrecognised.
 *
 * COMPLETED rather than IN_PROGRESS is the safe default here: restore reads the
 * history endpoint, which the backend already filters to completed sessions, and
 * resurrecting a workout as "in progress" would show the user a phantom active
 * session they never started.
 */
private fun String.toWorkoutStatus(): WorkoutStatus =
    WorkoutStatus.entries.firstOrNull { it.name == this } ?: WorkoutStatus.COMPLETED

/** Falls back to the ordinary case; a working set is what almost every set is. */
private fun String.toSetCategory(): SetCategory =
    SetCategory.entries.firstOrNull { it.name == this } ?: SetCategory.WORKING
