package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.AddWorkoutSetRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutSetRequest;
import com.myfitnesslog.dto.response.WorkoutSetResponse;
import com.myfitnesslog.mapper.WorkoutSetMapper;
import com.myfitnesslog.service.WorkoutSetSaveResult;
import com.myfitnesslog.service.WorkoutSetService;
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
 * REST endpoints for the performed sets within a workout exercise. HTTP concerns
 * only: validate, delegate to {@link WorkoutSetService}, map the result.
 * Immutability rules live in the service.
 */
@RestController
@RequestMapping("/api/v1")
public class WorkoutSetController {

    private final WorkoutSetService workoutSetService;
    private final WorkoutSetMapper workoutSetMapper;

    public WorkoutSetController(WorkoutSetService workoutSetService, WorkoutSetMapper workoutSetMapper) {
        this.workoutSetService = workoutSetService;
        this.workoutSetMapper = workoutSetMapper;
    }

    /** Idempotent add: 201 Created when new, 200 OK when an existing row was updated. */
    @PostMapping("/workout-exercises/{workoutExerciseId}/sets")
    public ResponseEntity<WorkoutSetResponse> addSet(
            @PathVariable UUID workoutExerciseId,
            @Valid @RequestBody AddWorkoutSetRequest request) {
        WorkoutSetSaveResult result = workoutSetService.addSet(workoutExerciseId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(workoutSetMapper.toResponse(result.workoutSet()));
    }

    @PutMapping("/workout-sets/{id}")
    public WorkoutSetResponse updateSet(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkoutSetRequest request) {
        return workoutSetMapper.toResponse(workoutSetService.updateSet(id, request));
    }

    @DeleteMapping("/workout-sets/{id}")
    public ResponseEntity<Void> deleteSet(@PathVariable UUID id) {
        workoutSetService.deleteSet(id);
        return ResponseEntity.noContent().build();
    }
}
