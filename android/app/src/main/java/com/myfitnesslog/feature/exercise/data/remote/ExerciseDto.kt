package com.myfitnesslog.feature.exercise.data.remote

import kotlinx.serialization.Serializable

/**
 * Network model for the `GET /api/v1/exercises` response, matching the finalized
 * `ExerciseResponse` contract (docs/API_SPECIFICATION.md).
 *
 * Nullable fields mirror the nullable columns in the contract. `id`/`categoryId`
 * are Strings for the same reason as [ExerciseCategoryDto].
 */
@Serializable
data class ExerciseDto(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String? = null,
    val instructions: String? = null,
    val equipment: String? = null,
)
