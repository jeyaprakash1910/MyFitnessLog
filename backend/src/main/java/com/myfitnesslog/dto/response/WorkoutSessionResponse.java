package com.myfitnesslog.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a workout session. routineId is exposed by id only (null for
 * manual workouts); status is the WorkoutStatus name. Nested exercises and sets
 * are added to the detail view in a later phase.
 */
public record WorkoutSessionResponse(
        UUID id,
        UUID routineId,
        String status,
        Instant startedAt,
        Instant endedAt,
        String notes
) {
}
