package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for starting a workout. The id is client-generated (idempotent
 * create). routineId is nullable — null denotes a manual/ad-hoc workout. The
 * owner is attached server-side. startedAt is a client-supplied domain timestamp
 * and is preserved exactly.
 */
public record StartWorkoutSessionRequest(

        @NotNull(message = "Workout session id is required.")
        UUID id,

        UUID routineId,

        @NotNull(message = "startedAt is required.")
        Instant startedAt,

        String notes
) {
}
