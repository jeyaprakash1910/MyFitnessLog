package com.myfitnesslog.feature.routine.data.remote

import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity

/**
 * Hand-written Entity → DTO mappers for routine uploads.
 *
 * The reverse direction (DTO → Entity) is deliberately absent: Version 1
 * synchronization is one-way (SYNC.md §6), so upload responses are used only to
 * confirm success, never to overwrite Room.
 *
 * Two local fields have no counterpart in the entity and are sent as null:
 * `description` and `displayOrder`. The Room schema never captured them because
 * no screen in ANDROID_FLOW edits either. Both are optional on the backend, so
 * omitting them is contract-valid; if routine descriptions or manual ordering are
 * added to the UI later, the entity gains the columns and these mappers follow.
 */
fun RoutineEntity.toCreateRequest(): CreateRoutineRequestDto =
    CreateRoutineRequestDto(
        id = id.toString(),
        name = name,
        description = null,
        displayOrder = null,
    )

fun RoutineEntity.toUpdateRequest(): UpdateRoutineRequestDto =
    UpdateRoutineRequestDto(
        name = name,
        description = null,
        displayOrder = null,
    )

fun RoutineExerciseEntity.toAddRequest(): AddRoutineExerciseRequestDto =
    AddRoutineExerciseRequestDto(
        id = id.toString(),
        exerciseId = exerciseId.toString(),
        // Room names the column displayOrder; the API calls it exerciseOrder.
        exerciseOrder = displayOrder,
        targetSets = targetSets,
        minTargetReps = minTargetReps,
        maxTargetReps = maxTargetReps,
        targetRestSeconds = targetRestSeconds,
        notes = notes,
    )

fun RoutineExerciseEntity.toUpdateRequest(): UpdateRoutineExerciseRequestDto =
    UpdateRoutineExerciseRequestDto(
        exerciseOrder = displayOrder,
        targetSets = targetSets,
        minTargetReps = minTargetReps,
        maxTargetReps = maxTargetReps,
        targetRestSeconds = targetRestSeconds,
        notes = notes,
    )
