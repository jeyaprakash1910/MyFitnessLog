package com.myfitnesslog.dto.response;

import java.util.UUID;

/**
 * Response body for an exercise.
 * Exposes only the documented, client-relevant fields. The category is
 * represented by its identifier (categoryId); category metadata is obtained
 * separately via GET /exercise-categories.
 */
public record ExerciseResponse(
        UUID id,
        UUID categoryId,
        String name,
        String description,
        String instructions,
        String equipment
) {
}
