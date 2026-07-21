package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request body for completing a workout. endedAt is the client-supplied domain
 * timestamp (when the workout actually finished) and is preserved exactly; notes
 * optionally captures the final workout-level notes.
 */
public record CompleteWorkoutSessionRequest(

        @NotNull(message = "endedAt is required.")
        Instant endedAt,

        String notes
) {
}
