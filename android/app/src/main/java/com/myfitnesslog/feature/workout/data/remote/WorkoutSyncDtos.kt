package com.myfitnesslog.feature.workout.data.remote

import com.myfitnesslog.core.data.remote.BigDecimalSerializer
import com.myfitnesslog.core.data.remote.InstantSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant

/**
 * Network models for the workout upload endpoints, mirroring the backend's
 * workout session / exercise / set request and response records
 * (docs/API_SPECIFICATION.md).
 *
 * Transport models only — the Room entities are never annotated for
 * serialization, and conversion lives in WorkoutSyncMappers. Timestamps and
 * decimals carry explicit serializers because kotlinx-serialization has no
 * built-in support for either, and decimals must preserve scale to match the
 * backend's NUMERIC columns exactly.
 *
 * Enum-valued fields (`status`, `setCategory`) are typed as `String` and carry
 * the enum name, so an unrecognised value from a newer backend deserializes
 * rather than throwing.
 */
@Serializable
data class StartWorkoutSessionRequestDto(
    val id: String,
    val routineId: String? = null,
    @Serializable(with = InstantSerializer::class)
    val startedAt: Instant,
    val notes: String? = null,
)

@Serializable
data class CompleteWorkoutSessionRequestDto(
    @Serializable(with = InstantSerializer::class)
    val endedAt: Instant,
    val notes: String? = null,
)

@Serializable
data class DiscardWorkoutSessionRequestDto(
    @Serializable(with = InstantSerializer::class)
    val endedAt: Instant,
    val notes: String? = null,
)

@Serializable
data class WorkoutSessionResponseDto(
    val id: String,
    val routineId: String? = null,
    val status: String,
    @Serializable(with = InstantSerializer::class)
    val startedAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val endedAt: Instant? = null,
    val notes: String? = null,
)

@Serializable
data class AddWorkoutExerciseRequestDto(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val targetSets: Int,
    val minTargetReps: Int,
    val maxTargetReps: Int,
    val targetRestSeconds: Int? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateWorkoutExerciseRequestDto(
    val exerciseName: String,
    val exerciseOrder: Int,
    val targetSets: Int,
    val minTargetReps: Int,
    val maxTargetReps: Int,
    val targetRestSeconds: Int? = null,
    val notes: String? = null,
)

@Serializable
data class WorkoutExerciseResponseDto(
    val id: String,
    val workoutSessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val targetSets: Int,
    val minTargetReps: Int,
    val maxTargetReps: Int,
    val targetRestSeconds: Int? = null,
    val notes: String? = null,
)

@Serializable
data class AddWorkoutSetRequestDto(
    val id: String,
    val setNumber: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal,
    val repetitions: Int,
    val setCategory: String,
    @Serializable(with = InstantSerializer::class)
    val startedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val finishedAt: Instant? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rpe: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rir: BigDecimal? = null,
    val isCompleted: Boolean,
)

@Serializable
data class UpdateWorkoutSetRequestDto(
    val setNumber: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal,
    val repetitions: Int,
    val setCategory: String,
    @Serializable(with = InstantSerializer::class)
    val startedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val finishedAt: Instant? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rpe: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rir: BigDecimal? = null,
    val isCompleted: Boolean,
)

@Serializable
data class WorkoutSetResponseDto(
    val id: String,
    val workoutExerciseId: String,
    val setNumber: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val weight: BigDecimal,
    val repetitions: Int,
    val setCategory: String,
    @Serializable(with = InstantSerializer::class)
    val startedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val finishedAt: Instant? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rpe: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val rir: BigDecimal? = null,
    val isCompleted: Boolean,
)
