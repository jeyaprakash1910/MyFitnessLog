package com.myfitnesslog.repository;

import com.myfitnesslog.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persistence access for Exercise.
 * Inherited JpaRepository methods cover the documented list and lookup-by-id
 * use cases. The search use case (GET /exercises/search) will introduce a
 * derived query when ExerciseService consumes it, so its semantics are decided
 * at the service layer; no custom queries are required yet.
 */
public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
}
