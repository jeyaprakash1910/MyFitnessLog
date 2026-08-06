package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddRoutineExerciseRequest;
import com.myfitnesslog.dto.request.ReorderRoutineExercisesRequest;
import com.myfitnesslog.dto.request.UpdateRoutineExerciseRequest;
import com.myfitnesslog.entity.RoutineExercise;

import java.util.List;
import java.util.UUID;

/**
 * Business operations for the exercises within a routine. Implementations own the
 * business rules (parent/exercise existence, idempotent add, atomic reorder) and
 * throw domain exceptions; they never deal with HTTP concerns.
 */
public interface RoutineExerciseService {

    /**
     * Idempotently adds an exercise to a routine by its client-supplied id: creates
     * it if new, or updates the existing row's mutable fields. Validates the parent
     * routine (active) and the referenced master exercise.
     */
    /**
     * A routine's exercises, in performance order.
     *
     * <p>Added for the read path (ADR-0017): a client restoring its local copy has
     * to be able to reconstruct what a routine contains, which nothing previously
     * exposed.
     */
    List<RoutineExercise> getExercises(UUID routineId);

    RoutineExerciseSaveResult addExercise(UUID routineId, AddRoutineExerciseRequest request);

    /** Updates a routine exercise's mutable fields; throws if it does not exist. */
    RoutineExercise updateExercise(UUID id, UpdateRoutineExerciseRequest request);

    /** Hard-deletes a routine exercise; a missing id is an idempotent no-op. */
    void deleteExercise(UUID id);

    /**
     * Atomically reorders a routine's exercises. The request must list exactly the
     * routine's exercises (no duplicates, omissions, or extras) or a
     * BusinessRuleException is thrown. Returns the exercises in their new order.
     */
    List<RoutineExercise> reorderExercises(UUID routineId, ReorderRoutineExercisesRequest request);
}
