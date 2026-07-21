package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for adding an exercise to a workout session. The id is
 * client-generated (idempotent add). exerciseName is the snapshot captured at
 * workout time (preserved even if the master exercise is later renamed); the
 * master is still referenced by exerciseId. Only permitted for IN_PROGRESS
 * sessions (enforced by the service).
 */
public record AddWorkoutExerciseRequest(

        @NotNull(message = "WorkoutExercise id is required.")
        UUID id,

        @NotNull(message = "exerciseId is required.")
        UUID exerciseId,

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
