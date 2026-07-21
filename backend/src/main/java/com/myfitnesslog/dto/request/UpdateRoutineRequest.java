package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a routine's mutable fields. The id (path), owner, and
 * audit metadata are never changed through this request.
 */
public record UpdateRoutineRequest(

        @NotBlank(message = "Routine name must not be blank.")
        @Size(max = 150, message = "Routine name must be at most 150 characters.")
        String name,

        String description,

        @PositiveOrZero(message = "displayOrder must be zero or positive.")
        Integer displayOrder
) {
}
