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
        /**
         * The routine's name when the workout started; null for a manual workout,
         * and for sessions recorded before this field existed. Snapshotted, so it
         * does not follow a later rename (ADR-0004).
         */
        String routineName,
        String status,
        Instant startedAt,
        Instant endedAt,
        String notes,
        /** Server-assigned audit timestamps (ADR-0006); see RoutineResponse. */
        Instant createdAt,
        Instant updatedAt
) {
}
