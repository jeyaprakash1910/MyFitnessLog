package com.myfitnesslog.service;

import com.myfitnesslog.entity.ExerciseCategory;

import java.util.List;

/**
 * Business operations for exercise categories.
 * Exposes only the operations required by documented use cases.
 */
public interface ExerciseCategoryService {

    /**
     * Returns all active (non-deleted) categories ordered by displayOrder.
     * Supports the documented GET /exercise-categories use case.
     */
    List<ExerciseCategory> getAllCategories();
}
