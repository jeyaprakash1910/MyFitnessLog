package com.myfitnesslog.controller;

import com.myfitnesslog.dto.response.ExerciseCategoryResponse;
import com.myfitnesslog.mapper.ExerciseCategoryMapper;
import com.myfitnesslog.service.ExerciseCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for exercise categories.
 * Handles only HTTP concerns: delegates to the service and maps the result.
 */
@RestController
@RequestMapping("/api/v1/exercise-categories")
public class ExerciseCategoryController {

    private final ExerciseCategoryService exerciseCategoryService;
    private final ExerciseCategoryMapper exerciseCategoryMapper;

    public ExerciseCategoryController(ExerciseCategoryService exerciseCategoryService,
                                      ExerciseCategoryMapper exerciseCategoryMapper) {
        this.exerciseCategoryService = exerciseCategoryService;
        this.exerciseCategoryMapper = exerciseCategoryMapper;
    }

    @GetMapping
    public List<ExerciseCategoryResponse> getAllCategories() {
        return exerciseCategoryMapper.toResponseList(exerciseCategoryService.getAllCategories());
    }
}
