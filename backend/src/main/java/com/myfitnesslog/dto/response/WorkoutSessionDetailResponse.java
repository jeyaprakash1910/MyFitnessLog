package com.myfitnesslog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The complete immutable workout snapshot returned by GET /workout-sessions/{id}:
 * the session metadata plus its exercises (in performed order), each with its sets
 * (in set-number order). Mirrors the Android Workout Detail screen. Assembled
 * entirely inside the service transaction.
 */
public record WorkoutSessionDetailResponse(
        UUID id,
        UUID routineId,
        /** Routine name captured at workout start; null for a manual workout. */
        String routineName,
        String status,
        Instant startedAt,
        Instant endedAt,
        String notes,
        /** Server-assigned audit timestamps (ADR-0006); see RoutineResponse. */
        Instant createdAt,
        Instant updatedAt,
        List<WorkoutExerciseDetailResponse> exercises
) {
}
