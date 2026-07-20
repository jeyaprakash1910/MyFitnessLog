package com.myfitnesslog.repository;

import com.myfitnesslog.entity.ExerciseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persistence access for ExerciseCategory.
 * Inherited JpaRepository methods cover the documented use cases
 * (list all categories, lookup by id); no custom queries are required.
 */
public interface ExerciseCategoryRepository extends JpaRepository<ExerciseCategory, UUID> {
}
