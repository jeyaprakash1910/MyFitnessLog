package com.myfitnesslog.service;

import com.myfitnesslog.entity.Exercise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business operations for exercises.
 * Exposes only the operations required by documented use cases.
 */
public interface ExerciseService {

    /**
     * Returns all active (non-deleted) exercises.
     * Supports the documented GET /exercises use case.
     */
    List<Exercise> getAllExercises();

    /**
     * Returns a single active (non-deleted) exercise by id, if present.
     * Supports the documented GET /exercises/{id} use case.
     */
    Optional<Exercise> getExerciseById(UUID id);

    /**
     * Returns active (non-deleted) exercises whose name contains the given
     * query, case-insensitively. Supports the documented GET /exercises/search
     * use case (search by name).
     */
    List<Exercise> searchExercises(String query);
}
