package com.myfitnesslog.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Request body for reordering a routine's exercises. {@code orderedIds} lists the
 * routine's RoutineExercise ids in the desired order; the new exerciseOrder is the
 * index of each id. The service validates the list covers exactly the routine's
 * exercises with no duplicates or omissions.
 */
public record ReorderRoutineExercisesRequest(

        @NotEmpty(message = "orderedIds must not be empty.")
        List<UUID> orderedIds
) {
}
