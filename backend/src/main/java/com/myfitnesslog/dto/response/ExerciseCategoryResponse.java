package com.myfitnesslog.dto.response;

import java.util.UUID;

/**
 * Response body for an exercise category.
 * Matches the documented resource shape in API_SPECIFICATION.md.
 */
public record ExerciseCategoryResponse(
        UUID id,
        String name,
        /**
         * Curated display position, ascending. The list endpoint already returns
         * categories in this order; exposing the value lets a client that stores
         * categories locally (Android caches them in Room) preserve that order
         * instead of falling back to alphabetical, which scrambles the intended
         * muscle-group grouping.
         */
        int displayOrder
) {
}
