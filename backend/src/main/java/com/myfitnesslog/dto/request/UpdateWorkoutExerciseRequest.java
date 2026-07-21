package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a workout exercise's mutable snapshot fields. The id
 * (path), parent session, and referenced master exercise are never changed here.
 * Permitted only while the parent session is IN_PROGRESS (enforced by the service).
 */
public record UpdateWorkoutExerciseRequest(

        @NotBlank(message = "exerciseName is required.")
        @Size(max = 150, message = "exerciseName must be at most 150 characters.")
        String exerciseName,

        @NotNull(message = "exerciseOrder is required.")
        @PositiveOrZero(message = "exerciseOrder must be zero or positive.")
        Integer exerciseOrder,

        @NotNull(message = "targetSets is required.")
        @Positive(message = "targetSets must be greater than zero.")
        Integer targetSets,

        @NotNull(message = "minTargetReps is required.")
        @Positive(message = "minTargetReps must be greater than zero.")
        Integer minTargetReps,

        @NotNull(message = "maxTargetReps is required.")
        @Positive(message = "maxTargetReps must be greater than zero.")
        Integer maxTargetReps,

        @PositiveOrZero(message = "targetRestSeconds must be zero or positive.")
        Integer targetRestSeconds,

        String notes
) {

    @AssertTrue(message = "maxTargetReps must be greater than or equal to minTargetReps.")
    public boolean isRepRangeValid() {
        return minTargetReps == null || maxTargetReps == null || maxTargetReps >= minTargetReps;
    }
}
