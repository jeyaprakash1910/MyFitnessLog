package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.CreateRoutineRequest;
import com.myfitnesslog.dto.request.UpdateRoutineRequest;
import com.myfitnesslog.entity.Routine;

import java.util.List;
import java.util.UUID;

/**
 * Business operations for routines. Implementations own the business rules
 * (default-user attachment, idempotent create, soft delete, duplication) and
 * throw domain exceptions; they never deal with HTTP concerns.
 */
public interface RoutineService {

    /** Active (non-deleted) routines, ordered by displayOrder. */
    List<Routine> getAllRoutines();

    /** A single active routine; throws ResourceNotFoundException if missing/deleted. */
    Routine getRoutine(UUID id);

    /**
     * Idempotently persists a routine by its client-supplied id: creates it if new,
     * or updates the existing routine's mutable fields. Attaches the default user.
     */
    RoutineSaveResult createRoutine(CreateRoutineRequest request);

    /** Updates an active routine's mutable fields; throws if missing/deleted. */
    Routine updateRoutine(UUID id, UpdateRoutineRequest request);

    /** Soft-deletes a routine (idempotent); throws if the routine does not exist. */
    void deleteRoutine(UUID id);

    /** Creates an independent copy of an active routine with a new id. */
    Routine duplicateRoutine(UUID id);
}
