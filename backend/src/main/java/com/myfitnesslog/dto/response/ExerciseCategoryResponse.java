package com.myfitnesslog.dto.response;

import java.util.UUID;

/**
 * Response body for an exercise category.
 * Matches the documented resource shape in API_SPECIFICATION.md.
 */
public record ExerciseCategoryResponse(
        UUID id,
        String name
) {
}
