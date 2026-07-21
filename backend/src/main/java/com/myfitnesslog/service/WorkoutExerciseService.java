package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddWorkoutExerciseRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutExerciseRequest;
import com.myfitnesslog.entity.WorkoutExercise;

import java.util.UUID;

/**
 * Business operations for the exercises within a workout session. Implementations
 * enforce the immutability rule (mutations only while the parent session is
 * IN_PROGRESS) and throw domain exceptions; they never deal with HTTP.
 */
public interface WorkoutExerciseService {

    /**
     * Idempotently adds an exercise to a workout by its client-supplied id. The
     * parent session must be IN_PROGRESS and the referenced master exercise must
     * exist. Snapshot fields (name, targets) are preserved as supplied.
     */
    WorkoutExerciseSaveResult addExercise(UUID sessionId, AddWorkoutExerciseRequest request);

    /** Updates a workout exercise's mutable snapshot fields; parent must be IN_PROGRESS. */
    WorkoutExercise updateExercise(UUID id, UpdateWorkoutExerciseRequest request);

    /** Hard-deletes a workout exercise (idempotent no-op if missing); parent must be IN_PROGRESS. */
    void deleteExercise(UUID id);
}
