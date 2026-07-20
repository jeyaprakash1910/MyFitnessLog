package com.myfitnesslog.controller;

import com.myfitnesslog.dto.response.ExerciseResponse;
import com.myfitnesslog.mapper.ExerciseMapper;
import com.myfitnesslog.service.ExerciseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for exercises.
 * Handles only HTTP concerns: delegates to the service and maps the result.
 */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final ExerciseMapper exerciseMapper;

    public ExerciseController(ExerciseService exerciseService, ExerciseMapper exerciseMapper) {
        this.exerciseService = exerciseService;
        this.exerciseMapper = exerciseMapper;
    }

    @GetMapping
    public List<ExerciseResponse> getAllExercises() {
        return exerciseMapper.toResponseList(exerciseService.getAllExercises());
    }

    @GetMapping("/search")
    public List<ExerciseResponse> searchExercises(@RequestParam(name = "q", required = false) String q) {
        return exerciseMapper.toResponseList(exerciseService.searchExercises(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExerciseResponse> getExerciseById(@PathVariable UUID id) {
        return exerciseService.getExerciseById(id)
                .map(exerciseMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
