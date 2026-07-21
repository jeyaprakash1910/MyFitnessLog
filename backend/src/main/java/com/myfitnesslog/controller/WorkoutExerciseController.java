package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.AddWorkoutExerciseRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutExerciseRequest;
import com.myfitnesslog.dto.response.WorkoutExerciseResponse;
import com.myfitnesslog.mapper.WorkoutExerciseMapper;
import com.myfitnesslog.service.WorkoutExerciseSaveResult;
import com.myfitnesslog.service.WorkoutExerciseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints for the exercises within a workout session. HTTP concerns only:
 * validate, delegate to {@link WorkoutExerciseService}, map the result. Immutability
 * rules live in the service.
 */
@RestController
@RequestMapping("/api/v1")
public class WorkoutExerciseController {

    private final WorkoutExerciseService workoutExerciseService;
    private final WorkoutExerciseMapper workoutExerciseMapper;

    public WorkoutExerciseController(
            WorkoutExerciseService workoutExerciseService,
            WorkoutExerciseMapper workoutExerciseMapper) {
        this.workoutExerciseService = workoutExerciseService;
        this.workoutExerciseMapper = workoutExerciseMapper;
    }

    /** Idempotent add: 201 Created when new, 200 OK when an existing row was updated. */
    @PostMapping("/workout-sessions/{sessionId}/exercises")
    public ResponseEntity<WorkoutExerciseResponse> addExercise(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AddWorkoutExerciseRequest request) {
        WorkoutExerciseSaveResult result = workoutExerciseService.addExercise(sessionId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(workoutExerciseMapper.toResponse(result.workoutExercise()));
    }

    @PutMapping("/workout-exercises/{id}")
    public WorkoutExerciseResponse updateExercise(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkoutExerciseRequest request) {
        return workoutExerciseMapper.toResponse(workoutExerciseService.updateExercise(id, request));
    }

    @DeleteMapping("/workout-exercises/{id}")
    public ResponseEntity<Void> deleteExercise(@PathVariable UUID id) {
        workoutExerciseService.deleteExercise(id);
        return ResponseEntity.noContent().build();
    }
}
