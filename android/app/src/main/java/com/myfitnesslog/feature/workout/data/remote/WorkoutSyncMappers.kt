package com.myfitnesslog.feature.workout.data.remote

import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity

/**
 * Hand-written Entity → DTO mappers for workout uploads.
 *
 * As with the routine mappers, there is no DTO → Entity direction: Version 1
 * synchronization is one-way, and completed workout history must never be
 * rewritten by a network response (SYNC.md §14).
 *
 * The session's terminal transitions require `endedAt`, which the entity models
 * as nullable because it is null while the workout is IN_PROGRESS. Mapping a
 * session that has not ended is a programming error, so it fails loudly rather
 * than inventing a timestamp — losing the true end time would corrupt history.
 */
fun WorkoutSessionEntity.toStartRequest(): StartWorkoutSessionRequestDto =
    StartWorkoutSessionRequestDto(
        id = id.toString(),
        routineId = routineId?.toString(),
        routineName = routineName,
        startedAt = startedAt,
        notes = notes,
    )

fun WorkoutSessionEntity.toCompleteRequest(): CompleteWorkoutSessionRequestDto =
    CompleteWorkoutSessionRequestDto(
        endedAt = requireEndedAt(),
        notes = notes,
    )

fun WorkoutSessionEntity.toDiscardRequest(): DiscardWorkoutSessionRequestDto =
    DiscardWorkoutSessionRequestDto(
        endedAt = requireEndedAt(),
        notes = notes,
    )

private fun WorkoutSessionEntity.requireEndedAt() =
    requireNotNull(endedAt) {
        "Cannot upload a terminal transition for session $id: endedAt is null " +
            "(status=$status). Only completed or discarded sessions have an end time."
    }

fun WorkoutExerciseEntity.toAddRequest(): AddWorkoutExerciseRequestDto =
    AddWorkoutExerciseRequestDto(
        id = id.toString(),
        exerciseId = exerciseId.toString(),
        exerciseName = exerciseName,
        exerciseOrder = exerciseOrder,
        targetSets = targetSets,
        minTargetReps = minTargetReps,
        maxTargetReps = maxTargetReps,
        targetRestSeconds = targetRestSeconds,
        notes = notes,
    )

fun WorkoutExerciseEntity.toUpdateRequest(): UpdateWorkoutExerciseRequestDto =
    UpdateWorkoutExerciseRequestDto(
        exerciseName = exerciseName,
        exerciseOrder = exerciseOrder,
        targetSets = targetSets,
        minTargetReps = minTargetReps,
        maxTargetReps = maxTargetReps,
        targetRestSeconds = targetRestSeconds,
        notes = notes,
    )

fun WorkoutSetEntity.toAddRequest(): AddWorkoutSetRequestDto =
    AddWorkoutSetRequestDto(
        id = id.toString(),
        setNumber = setNumber,
        weight = weight,
        repetitions = repetitions,
        setCategory = setCategory.name,
        startedAt = startedAt,
        finishedAt = finishedAt,
        rpe = rpe,
        rir = rir,
        isCompleted = isCompleted,
    )

fun WorkoutSetEntity.toUpdateRequest(): UpdateWorkoutSetRequestDto =
    UpdateWorkoutSetRequestDto(
        setNumber = setNumber,
        weight = weight,
        repetitions = repetitions,
        setCategory = setCategory.name,
        startedAt = startedAt,
        finishedAt = finishedAt,
        rpe = rpe,
        rir = rir,
        isCompleted = isCompleted,
    )
