package com.myfitnesslog.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * One exercise within the workout detail snapshot: the snapshotted exercise fields
 * plus its performed sets in set-number order. Assembled inside the service
 * transaction (no lazy loading in the mapper).
 */
public record WorkoutExerciseDetailResponse(
        UUID id,
        UUID exerciseId,
        String exerciseName,
        int exerciseOrder,
        int targetSets,
        int minTargetReps,
        int maxTargetReps,
        Integer targetRestSeconds,
        String notes,
        List<WorkoutSetResponse> sets
) {
}
