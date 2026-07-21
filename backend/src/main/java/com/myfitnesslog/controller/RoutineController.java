package com.myfitnesslog.controller;

import com.myfitnesslog.dto.request.CreateRoutineRequest;
import com.myfitnesslog.dto.request.UpdateRoutineRequest;
import com.myfitnesslog.dto.response.RoutineResponse;
import com.myfitnesslog.mapper.RoutineMapper;
import com.myfitnesslog.service.RoutineSaveResult;
import com.myfitnesslog.service.RoutineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * REST endpoints for routines. Handles only HTTP concerns: validates the request,
 * delegates to the service, and maps the result. Business rules and exceptions
 * live in {@link RoutineService}.
 */
@RestController
@RequestMapping("/api/v1/routines")
public class RoutineController {

    private final RoutineService routineService;
    private final RoutineMapper routineMapper;

    public RoutineController(RoutineService routineService, RoutineMapper routineMapper) {
        this.routineService = routineService;
        this.routineMapper = routineMapper;
    }

    @GetMapping
    public List<RoutineResponse> getAllRoutines() {
        return routineMapper.toResponseList(routineService.getAllRoutines());
    }

    @GetMapping("/{id}")
    public RoutineResponse getRoutine(@PathVariable UUID id) {
        return routineMapper.toResponse(routineService.getRoutine(id));
    }

    /**
     * Idempotent create: 201 Created when the routine is new, 200 OK when an
     * existing routine (same client id) was updated in place.
     */
    @PostMapping
    public ResponseEntity<RoutineResponse> createRoutine(@Valid @RequestBody CreateRoutineRequest request) {
        RoutineSaveResult result = routineService.createRoutine(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(routineMapper.toResponse(result.routine()));
    }

    @PutMapping("/{id}")
    public RoutineResponse updateRoutine(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoutineRequest request) {
        return routineMapper.toResponse(routineService.updateRoutine(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoutine(@PathVariable UUID id) {
        routineService.deleteRoutine(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<RoutineResponse> duplicateRoutine(@PathVariable UUID id) {
        RoutineResponse response = routineMapper.toResponse(routineService.duplicateRoutine(id));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
