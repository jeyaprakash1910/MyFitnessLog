package com.myfitnesslog.service;

import com.myfitnesslog.entity.Exercise;
import com.myfitnesslog.repository.ExerciseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link ExerciseService}.
 * Applies documented business rules (exclude soft-deleted exercises,
 * case-insensitive name search) at the service layer, keeping the repository
 * free of business concerns.
 */
@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciseRepository exerciseRepository;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Exercise> getAllExercises() {
        return exerciseRepository.findAll().stream()
                .filter(exercise -> !exercise.isDeleted())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Exercise> getExerciseById(UUID id) {
        return exerciseRepository.findById(id)
                .filter(exercise -> !exercise.isDeleted());
    }

    /**
     * Performs an in-memory search intentionally. The exercise catalog is
     * expected to remain relatively small, so correctness is prioritized over
     * database-level optimization. Repository optimization (for example a
     * derived query or fetch strategy) will be introduced only if justified by
     * profiling or a documented requirement.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Exercise> searchExercises(String query) {
        String normalized = (query == null) ? "" : query.trim().toLowerCase();
        return exerciseRepository.findAll().stream()
                .filter(exercise -> !exercise.isDeleted())
                .filter(exercise -> exercise.getName().toLowerCase().contains(normalized))
                .toList();
    }
}
