package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.DiscardWorkoutSessionRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.dto.response.WorkoutSessionDetailResponse;
import com.myfitnesslog.entity.WorkoutSession;

import java.util.List;
import java.util.UUID;

/**
 * Business operations for workout sessions. Implementations own the lifecycle
 * rules (idempotent start, valid status transitions, default-user attachment,
 * timestamp preservation) and throw domain exceptions; they never deal with HTTP.
 */
public interface WorkoutSessionService {

    /** Finished workouts (COMPLETED or DISCARDED), newest first; excludes active. */
    List<WorkoutSession> getHistory();

    /** A single workout by id (any status); throws ResourceNotFoundException if missing. */
    WorkoutSession getWorkout(UUID id);

    /**
     * The complete immutable workout snapshot (session + exercises + sets), assembled
     * entirely inside the transaction and ordered by exercise then set number. Throws
     * ResourceNotFoundException if the session does not exist.
     */
    WorkoutSessionDetailResponse getWorkoutDetail(UUID id);

    /**
     * Idempotently starts a workout by its client-supplied id: creates it if new,
     * or converges the existing session's start fields. Attaches the default user
     * and preserves the client's startedAt.
     */
    WorkoutSessionSaveResult startWorkout(StartWorkoutSessionRequest request);

    /**
     * Completes a workout. IN_PROGRESS → COMPLETED (records endedAt); already
     * COMPLETED is an idempotent no-op; DISCARDED → BusinessRuleException.
     */
    WorkoutSession completeWorkout(UUID id, CompleteWorkoutSessionRequest request);

    /**
     * Discards a workout. IN_PROGRESS → DISCARDED (records endedAt); already
     * DISCARDED is an idempotent no-op; COMPLETED → BusinessRuleException.
     */
    WorkoutSession discardWorkout(UUID id, DiscardWorkoutSessionRequest request);
}
