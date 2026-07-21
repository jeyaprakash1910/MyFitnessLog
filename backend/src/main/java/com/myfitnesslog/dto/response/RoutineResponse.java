package com.myfitnesslog.dto.response;

import java.util.UUID;

/**
 * Response body for a routine. Exposes only the client-relevant fields; the owner
 * (single-user V1), soft-delete flag, and audit metadata are intentionally not
 * exposed. Routine exercises are added to this resource in a later phase.
 */
public record RoutineResponse(
        UUID id,
        String name,
        String description,
        int displayOrder
) {
}
