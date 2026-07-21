package com.myfitnesslog.service;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.DiscardWorkoutSessionRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.dto.response.WorkoutExerciseDetailResponse;
import com.myfitnesslog.dto.response.WorkoutSessionDetailResponse;
import com.myfitnesslog.dto.response.WorkoutSetResponse;
import com.myfitnesslog.entity.Routine;
import com.myfitnesslog.entity.WorkoutExercise;
import com.myfitnesslog.entity.WorkoutSession;
import com.myfitnesslog.entity.WorkoutStatus;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.mapper.WorkoutSetMapper;
import com.myfitnesslog.repository.RoutineRepository;
import com.myfitnesslog.repository.WorkoutExerciseRepository;
import com.myfitnesslog.repository.WorkoutSessionRepository;
import com.myfitnesslog.repository.WorkoutSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link WorkoutSessionService}. Owns the workout lifecycle: idempotent
 * start, and valid COMPLETED/DISCARDED transitions with idempotent replay.
 * History is filtered/ordered in the service (the small in-memory approach used
 * elsewhere while the data set is small).
 */
@Service
public class WorkoutSessionServiceImpl implements WorkoutSessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final RoutineRepository routineRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;
    private final WorkoutSetMapper workoutSetMapper;
    private final DefaultUserProvider defaultUserProvider;

    public WorkoutSessionServiceImpl(
            WorkoutSessionRepository sessionRepository,
            RoutineRepository routineRepository,
            WorkoutExerciseRepository workoutExerciseRepository,
            WorkoutSetRepository workoutSetRepository,
            WorkoutSetMapper workoutSetMapper,
            DefaultUserProvider defaultUserProvider) {
        this.sessionRepository = sessionRepository;
        this.routineRepository = routineRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.workoutSetRepository = workoutSetRepository;
        this.workoutSetMapper = workoutSetMapper;
        this.defaultUserProvider = defaultUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutSession> getHistory() {
        return sessionRepository.findAll().stream()
                .filter(session -> session.getStatus() != WorkoutStatus.IN_PROGRESS)
                .sorted(Comparator.comparing(WorkoutSession::getStartedAt).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutSession getWorkout(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workout session not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutSessionDetailResponse getWorkoutDetail(UUID id) {
        WorkoutSession session = getWorkout(id);
        // Assemble the snapshot inside the transaction (OSIV is disabled): load the
        // ordered exercises, then each exercise's ordered sets. No lazy traversal
        // happens in the mapper — the sets are fetched here.
        List<WorkoutExerciseDetailResponse> exercises =
                workoutExerciseRepository.findByWorkoutSession_IdOrderByExerciseOrderAsc(id).stream()
                        .map(this::toExerciseDetail)
                        .toList();
        return new WorkoutSessionDetailResponse(
                session.getId(),
                session.getRoutine() == null ? null : session.getRoutine().getId(),
                session.getStatus().name(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getNotes(),
                exercises);
    }

    private WorkoutExerciseDetailResponse toExerciseDetail(WorkoutExercise exercise) {
        List<WorkoutSetResponse> sets =
                workoutSetRepository.findByWorkoutExercise_IdOrderBySetNumberAsc(exercise.getId()).stream()
                        .map(workoutSetMapper::toResponse)
                        .toList();
        return new WorkoutExerciseDetailResponse(
                exercise.getId(),
                exercise.getExercise().getId(),
                exercise.getExerciseName(),
                exercise.getExerciseOrder(),
                exercise.getTargetSets(),
                exercise.getMinTargetReps(),
                exercise.getMaxTargetReps(),
                exercise.getTargetRestSeconds(),
                exercise.getNotes(),
                sets);
    }

    @Override
    @Transactional
    public WorkoutSessionSaveResult startWorkout(StartWorkoutSessionRequest request) {
        Routine routine = resolveRoutine(request.routineId());
        WorkoutSession existing = sessionRepository.findById(request.id()).orElse(null);
        if (existing != null) {
            // Idempotent replay: converge the start fields without touching the
            // lifecycle (status/endedAt), so a replay never reopens a finished workout.
            existing.setRoutine(routine);
            existing.setStartedAt(request.startedAt());
            existing.setNotes(request.notes());
            return new WorkoutSessionSaveResult(sessionRepository.save(existing), false);
        }
        WorkoutSession session = new WorkoutSession();
        session.setId(request.id());
        session.setUser(defaultUserProvider.getReference());
        session.setRoutine(routine);
        session.setStatus(WorkoutStatus.IN_PROGRESS);
        session.setStartedAt(request.startedAt());
        session.setNotes(request.notes());
        return new WorkoutSessionSaveResult(sessionRepository.save(session), true);
    }

    @Override
    @Transactional
    public WorkoutSession completeWorkout(UUID id, CompleteWorkoutSessionRequest request) {
        return finishWorkout(id, WorkoutStatus.COMPLETED, request.endedAt(), request.notes());
    }

    @Override
    @Transactional
    public WorkoutSession discardWorkout(UUID id, DiscardWorkoutSessionRequest request) {
        return finishWorkout(id, WorkoutStatus.DISCARDED, request.endedAt(), request.notes());
    }

    /**
     * Applies a terminal transition. From IN_PROGRESS → target (recording endedAt);
     * an idempotent no-op when already in the target state; any other status is an
     * illegal transition (409).
     */
    private WorkoutSession finishWorkout(UUID id, WorkoutStatus target, Instant endedAt, String notes) {
        WorkoutSession session = getWorkout(id);
        WorkoutStatus current = session.getStatus();
        if (current == target) {
            return session; // idempotent replay — no mutation
        }
        if (current != WorkoutStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "Cannot " + verb(target) + " a " + current.name().toLowerCase() + " workout.");
        }
        session.setStatus(target);
        session.setEndedAt(endedAt);
        if (notes != null) {
            session.setNotes(notes);
        }
        return sessionRepository.save(session);
    }

    private Routine resolveRoutine(UUID routineId) {
        if (routineId == null) {
            return null;
        }
        return routineRepository.findById(routineId)
                .orElseThrow(() -> new ResourceNotFoundException("Routine not found."));
    }

    private String verb(WorkoutStatus target) {
        return target == WorkoutStatus.COMPLETED ? "complete" : "discard";
    }
}
