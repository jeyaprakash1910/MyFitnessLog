package com.myfitnesslog.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a performed set. The parent workout exercise is exposed by id
 * only; setCategory is the enum name. Used by the set add/update endpoints and
 * nested in the workout detail view.
 */
public record WorkoutSetResponse(
        UUID id,
        UUID workoutExerciseId,
        int setNumber,
        BigDecimal weight,
        int repetitions,
        String setCategory,
        Instant startedAt,
        Instant finishedAt,
        BigDecimal rpe,
        BigDecimal rir,
        boolean isCompleted,
        /** Server-assigned audit timestamps (ADR-0006); see RoutineResponse. */
        Instant createdAt,
        Instant updatedAt
) {
}
