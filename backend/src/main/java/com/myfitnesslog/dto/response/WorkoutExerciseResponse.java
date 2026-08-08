package com.myfitnesslog.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a workout exercise (flat form, used by the add/update
 * endpoints). The parent session and master exercise are exposed by id only —
 * reading a lazy proxy's id issues no query (open-session-in-view is disabled).
 * The nested detail view uses {@link WorkoutExerciseDetailResponse}.
 */
public record WorkoutExerciseResponse(
        UUID id,
        UUID workoutSessionId,
        UUID exerciseId,
        String exerciseName,
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
