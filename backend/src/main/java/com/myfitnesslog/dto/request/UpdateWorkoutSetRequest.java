package com.myfitnesslog.dto.request;

import com.myfitnesslog.entity.SetCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for updating a performed set's measured fields. The id (path),
 * parent workout exercise, and its session are never changed here. Permitted only
 * while the parent session is IN_PROGRESS (enforced by the service).
 */
public record UpdateWorkoutSetRequest(

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
