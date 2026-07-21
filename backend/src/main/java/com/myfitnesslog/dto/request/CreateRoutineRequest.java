package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for creating a routine. The id is client-generated (UUIDv4,
 * offline-first) so the create is idempotent: re-submitting the same id updates
 * the existing routine instead of creating a duplicate (see RoutineService).
 * The owner is attached server-side (V1 is single-user; the client sends no
 * userId).
 */
public record CreateRoutineRequest(

        @NotNull(message = "Routine id is required.")
        UUID id,

        @NotBlank(message = "Routine name must not be blank.")
        @Size(max = 150, message = "Routine name must be at most 150 characters.")
        String name,

        String description,

        @PositiveOrZero(message = "displayOrder must be zero or positive.")
        Integer displayOrder
) {
}
