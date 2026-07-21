package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * Request body for adding an exercise to a routine. The id is client-generated so
 * the add is idempotent (re-submitting the same id updates the existing row). The
 * referenced master exercise is identified by exerciseId; the parent routine comes
 * from the path.
 */
public record AddRoutineExerciseRequest(

        @NotNull(message = "RoutineExercise id is required.")
        UUID id,

        @NotNull(message = "exerciseId is required.")
        UUID exerciseId,

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
