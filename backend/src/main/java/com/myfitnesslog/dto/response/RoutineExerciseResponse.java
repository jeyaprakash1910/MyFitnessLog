package com.myfitnesslog.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a routine exercise. Follows the same convention as
 * ExerciseResponse: the referenced master exercise is exposed by id only
 * (exerciseId), not by name — clients already hold the exercise library and
 * resolve names locally. Exposing only the id also keeps mapping free of any lazy
 * association access (open-session-in-view is disabled).
 */
public record RoutineExerciseResponse(
        UUID id,
        UUID exerciseId,
        int exerciseOrder,
        int targetSets,
        int minTargetReps,
        int maxTargetReps,
        Integer targetRestSeconds,
        String notes,
        /** Server-assigned audit timestamps (ADR-0006); see RoutineResponse. */
        Instant createdAt,
        Instant updatedAt
) {
}
