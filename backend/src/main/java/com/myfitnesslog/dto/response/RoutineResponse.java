package com.myfitnesslog.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a routine. Exposes only the client-relevant fields; the owner
 * (single-user V1), soft-delete flag, and audit metadata are intentionally not
 * exposed. Routine exercises are added to this resource in a later phase.
 */
public record RoutineResponse(
        UUID id,
        String name,
        String description,
        int displayOrder,
        /**
         * Audit timestamps, server-assigned (ADR-0006). {@code updatedAt} is the
         * field ADR-0017 Stage 3 arbitrates last-write-wins on, and it is
         * deliberately the *server's* value: device clocks drift and can be set
         * by the user, so a device-authoritative timestamp would let a phone with
         * a fast clock win every conflict forever.
         */
        Instant createdAt,
        Instant updatedAt
) {
}
