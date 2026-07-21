package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.AddRoutineExerciseRequest;
import com.myfitnesslog.dto.request.ReorderRoutineExercisesRequest;
import com.myfitnesslog.dto.request.UpdateRoutineExerciseRequest;
import com.myfitnesslog.dto.response.RoutineExerciseResponse;
import com.myfitnesslog.mapper.RoutineExerciseMapper;
import com.myfitnesslog.service.RoutineExerciseSaveResult;
import com.myfitnesslog.service.RoutineExerciseService;
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

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for the exercises within a routine. HTTP concerns only:
 * validate, delegate to {@link RoutineExerciseService}, map the result. The paths
 * span two resource groups (nested under a routine, and the flat
 * /routine-exercises collection), so they are declared per-method.
 */
@RestController
@RequestMapping("/api/v1")
public class RoutineExerciseController {

    private final RoutineExerciseService routineExerciseService;
    private final RoutineExerciseMapper routineExerciseMapper;

    public RoutineExerciseController(
            RoutineExerciseService routineExerciseService,
            RoutineExerciseMapper routineExerciseMapper) {
        this.routineExerciseService = routineExerciseService;
        this.routineExerciseMapper = routineExerciseMapper;
    }

    /** Idempotent add: 201 Created when new, 200 OK when an existing row was updated. */
    @PostMapping("/routines/{routineId}/exercises")
    public ResponseEntity<RoutineExerciseResponse> addExercise(
            @PathVariable UUID routineId,
            @Valid @RequestBody AddRoutineExerciseRequest request) {
        RoutineExerciseSaveResult result = routineExerciseService.addExercise(routineId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(routineExerciseMapper.toResponse(result.routineExercise()));
    }

    @PutMapping("/routine-exercises/{id}")
    public RoutineExerciseResponse updateExercise(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoutineExerciseRequest request) {
        return routineExerciseMapper.toResponse(routineExerciseService.updateExercise(id, request));
    }

    @DeleteMapping("/routine-exercises/{id}")
    public ResponseEntity<Void> deleteExercise(@PathVariable UUID id) {
        routineExerciseService.deleteExercise(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/routines/{routineId}/exercise-order")
    public List<RoutineExerciseResponse> reorderExercises(
            @PathVariable UUID routineId,
            @Valid @RequestBody ReorderRoutineExercisesRequest request) {
        return routineExerciseMapper.toResponseList(
                routineExerciseService.reorderExercises(routineId, request));
    }
}
