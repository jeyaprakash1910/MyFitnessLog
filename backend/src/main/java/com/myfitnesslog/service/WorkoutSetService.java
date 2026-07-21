package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddWorkoutSetRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutSetRequest;
import com.myfitnesslog.entity.WorkoutSet;

import java.util.UUID;

/**
 * Business operations for the sets within a workout exercise. Implementations
 * enforce the immutability rule (mutations only while the owning session is
 * IN_PROGRESS) and throw domain exceptions; they never deal with HTTP.
 */
public interface WorkoutSetService {

    /**
     * Idempotently adds a set to a workout exercise by its client-supplied id. The
     * owning session must be IN_PROGRESS.
     */
    WorkoutSetSaveResult addSet(UUID workoutExerciseId, AddWorkoutSetRequest request);

    /** Updates a set's measured fields; owning session must be IN_PROGRESS. */
    WorkoutSet updateSet(UUID id, UpdateWorkoutSetRequest request);

    /** Hard-deletes a set (idempotent no-op if missing); owning session must be IN_PROGRESS. */
    void deleteSet(UUID id);
}
