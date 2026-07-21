package com.myfitnesslog.dto.request;

import com.myfitnesslog.entity.SetCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request body for adding a performed set to a workout exercise. The id and
 * setNumber are client-generated. weight/rpe/rir use BigDecimal to match the
 * NUMERIC columns exactly. startedAt/finishedAt are client-supplied domain
 * timestamps. Only permitted for IN_PROGRESS sessions (enforced by the service).
 */
public record AddWorkoutSetRequest(

        @NotNull(message = "WorkoutSet id is required.")
        UUID id,

        @NotNull(message = "setNumber is required.")
        @Positive(message = "setNumber must be greater than zero.")
        Integer setNumber,

        @NotNull(message = "weight is required.")
        @DecimalMin(value = "0.0", message = "weight must be zero or positive.")
        BigDecimal weight,

        @NotNull(message = "repetitions is required.")
        @PositiveOrZero(message = "repetitions must be zero or positive.")
        Integer repetitions,

        @NotNull(message = "setCategory is required.")
        SetCategory setCategory,

        Instant startedAt,

        Instant finishedAt,

        @DecimalMin(value = "1.0", message = "rpe must be between 1 and 10.")
        @DecimalMax(value = "10.0", message = "rpe must be between 1 and 10.")
        BigDecimal rpe,

        @DecimalMin(value = "0.0", message = "rir must be zero or positive.")
        BigDecimal rir,

        @NotNull(message = "isCompleted is required.")
        Boolean isCompleted
) {
}
