package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.DiscardWorkoutSessionRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.dto.response.WorkoutSessionDetailResponse;
import com.myfitnesslog.dto.response.WorkoutSessionResponse;
import com.myfitnesslog.mapper.WorkoutSessionMapper;
import com.myfitnesslog.service.WorkoutSessionSaveResult;
import com.myfitnesslog.service.WorkoutSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for workout sessions. HTTP concerns only: validate, delegate to
 * {@link WorkoutSessionService}, map the result. Lifecycle rules live in the
 * service.
 */
@RestController
@RequestMapping("/api/v1/workout-sessions")
public class WorkoutSessionController {

    private final WorkoutSessionService workoutSessionService;
    private final WorkoutSessionMapper workoutSessionMapper;

    public WorkoutSessionController(
            WorkoutSessionService workoutSessionService,
            WorkoutSessionMapper workoutSessionMapper) {
        this.workoutSessionService = workoutSessionService;
        this.workoutSessionMapper = workoutSessionMapper;
    }

    /** Workout history: completed sessions only, newest first. */
    @GetMapping
    public List<WorkoutSessionResponse> getHistory() {
        return workoutSessionMapper.toResponseList(workoutSessionService.getHistory());
    }

    /** Workout detail: the complete immutable snapshot (session + exercises + sets). */
    @GetMapping("/{id}")
    public WorkoutSessionDetailResponse getWorkout(@PathVariable UUID id) {
        return workoutSessionService.getWorkoutDetail(id);
    }

    /** Idempotent start: 201 Created when new, 200 OK when an existing session converged. */
    @PostMapping
    public ResponseEntity<WorkoutSessionResponse> startWorkout(
            @Valid @RequestBody StartWorkoutSessionRequest request) {
        WorkoutSessionSaveResult result = workoutSessionService.startWorkout(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(workoutSessionMapper.toResponse(result.session()));
    }

    @PutMapping("/{id}/complete")
    public WorkoutSessionResponse completeWorkout(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteWorkoutSessionRequest request) {
        return workoutSessionMapper.toResponse(workoutSessionService.completeWorkout(id, request));
    }

    @PutMapping("/{id}/discard")
    public WorkoutSessionResponse discardWorkout(
            @PathVariable UUID id,
            @Valid @RequestBody DiscardWorkoutSessionRequest request) {
        return workoutSessionMapper.toResponse(workoutSessionService.discardWorkout(id, request));
    }
}
