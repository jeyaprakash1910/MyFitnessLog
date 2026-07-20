package com.myfitnesslog.service;

import com.myfitnesslog.entity.ExerciseCategory;
import com.myfitnesslog.repository.ExerciseCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of {@link ExerciseCategoryService}.
 * Applies the documented business rules (exclude soft-deleted categories,
 * order by displayOrder) at the service layer, keeping the repository free
 * of business concerns.
 */
@Service
public class ExerciseCategoryServiceImpl implements ExerciseCategoryService {

    private final ExerciseCategoryRepository exerciseCategoryRepository;

    public ExerciseCategoryServiceImpl(ExerciseCategoryRepository exerciseCategoryRepository) {
        this.exerciseCategoryRepository = exerciseCategoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExerciseCategory> getAllCategories() {
        return exerciseCategoryRepository.findAll().stream()
                .filter(category -> !category.isDeleted())
                .sorted(Comparator.comparingInt(ExerciseCategory::getDisplayOrder))
                .toList();
    }
}
