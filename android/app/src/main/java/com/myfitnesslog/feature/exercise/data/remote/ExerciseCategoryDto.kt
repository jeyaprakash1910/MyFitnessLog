package com.myfitnesslog.feature.exercise.data.remote

import kotlinx.serialization.Serializable

/**
 * Network model for the `GET /api/v1/exercise-categories` response, matching the
 * finalized `ExerciseCategoryResponse` contract (docs/API_SPECIFICATION.md).
 *
 * `id` is kept as a String here: the backend serializes UUIDs as JSON strings,
 * and parsing to [java.util.UUID] happens in the mapper rather than requiring a
 * custom serializer. DTOs are deliberately separate from Room entities.
 */
@Serializable
data class ExerciseCategoryDto(
    val id: String,
    val name: String,
    /**
     * Curated display position from the backend. Defaulted so a response from an
     * older backend that does not send the field still deserializes, in which
     * case every category shares position 0 and the name tiebreak orders them —
     * the previous behaviour.
     */
    val displayOrder: Int = 0,
)
