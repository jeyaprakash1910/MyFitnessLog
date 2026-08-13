package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

        /**
         * The routine's name as the client saw it at workout start. Sent by the
         * client rather than resolved here, because the snapshot must record what
         * the user was looking at, and the routine may since have been renamed or
         * soft-deleted on either side.
         */
        @Size(max = 100, message = "routineName must be at most 100 characters.")
        String routineName,

        @NotNull(message = "startedAt is required.")
        Instant startedAt,

        String notes
) {
}
