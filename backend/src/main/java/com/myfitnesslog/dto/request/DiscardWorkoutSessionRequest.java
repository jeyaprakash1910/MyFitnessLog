package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request body for discarding a workout. Mirrors {@link CompleteWorkoutSessionRequest}:
 * endedAt is the client-supplied domain timestamp (when the workout was discarded)
 * and is preserved exactly; notes is optional.
 */
public record DiscardWorkoutSessionRequest(

        @NotNull(message = "endedAt is required.")
        Instant endedAt,

        String notes
) {
}
