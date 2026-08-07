package com.myfitnesslog.feature.routine.data.remote

import kotlinx.serialization.Serializable

/**
 * Network models for the routine upload endpoints, mirroring the backend's
 * `CreateRoutineRequest` / `UpdateRoutineRequest` / `RoutineResponse` and the
 * routine-exercise equivalents (docs/API_SPECIFICATION.md).
 *
 * These are transport models only and stay completely separate from the Room
 * entities: entities are never annotated for serialization, and the conversion
 * lives in RoutineSyncMappers. Identifiers are `String` rather than `UUID`,
 * matching the convention established by ExerciseDto.
 */
@Serializable
data class CreateRoutineRequestDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val displayOrder: Int? = null,
)

@Serializable
data class UpdateRoutineRequestDto(
    val name: String,
    val description: String? = null,
    val displayOrder: Int? = null,
)

@Serializable
data class RoutineResponseDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val displayOrder: Int = 0,
)

@Serializable
data class AddRoutineExerciseRequestDto(
    val id: String,
    val exerciseId: String,
    val exerciseOrder: Int,
    val targetSets: Int,
    val minTargetReps: Int,
    val maxTargetReps: Int,
    val targetRestSeconds: Int? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateRoutineExerciseRequestDto(
    val exerciseOrder: Int,
    val targetSets: Int,
    val minTargetReps: Int,
    val maxTargetReps: Int,
    val targetRestSeconds: Int? = null,
    val notes: String? = null,
)

@Serializable
data class RoutineExerciseResponseDto(
    val id: String,
    val exerciseId: String,
    val exerciseOrder: Int,
    val targetSets: Int,
    val minTargetReps: Int,
    val maxTargetReps: Int,
    val targetRestSeconds: Int? = null,
    val notes: String? = null,
)

/**
 * Network model for `GET /api/v1/routines/{id}` (ADR-0017).
 *
 * Distinct from [RoutineResponseDto], which the list endpoint returns: a summary
 * identifies a routine, this one carries enough to rebuild it. [exercises] is
 * defaulted to empty so a response from a backend predating the read path still
 * deserializes rather than failing the whole restore.
 */
@Serializable
data class RoutineDetailResponseDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val displayOrder: Int = 0,
    val exercises: List<RoutineExerciseResponseDto> = emptyList(),
)
