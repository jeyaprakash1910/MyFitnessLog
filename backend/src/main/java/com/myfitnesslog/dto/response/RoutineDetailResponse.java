package com.myfitnesslog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network model for {@code GET /api/v1/routines/{id}}
 * (docs/API_SPECIFICATION.md).
 *
 * <p>A routine without its exercises is not a routine, it is a name. The list
 * endpoint returns {@link RoutineResponse} because a picker only needs names, but
 * anything reconstructing a routine needs what is in it. Until now nothing could:
 * no read endpoint exposed routine exercises at all, so a client could write a
 * routine to the backend and never read it back (ADR-0017).
 *
 * <p>Shaped deliberately like {@link WorkoutSessionDetailResponse}: a list
 * endpoint returning summaries and a by-id endpoint returning the summary plus its
 * children. One pattern for both aggregates rather than two conventions to
 * remember.
 *
 * <p>Adding {@code exercises} to the by-id response is additive for existing
 * consumers, which see one extra field and are free to ignore it.
 *
 * @param exercises the routine's exercises in {@code exerciseOrder}, excluding
 *                  any that were removed
 */
public record RoutineDetailResponse(
        UUID id,
        String name,
        String description,
        int displayOrder,
        /** Server-assigned audit timestamps (ADR-0006); see RoutineResponse. */
        Instant createdAt,
        Instant updatedAt,
        List<RoutineExerciseResponse> exercises
) {
}
