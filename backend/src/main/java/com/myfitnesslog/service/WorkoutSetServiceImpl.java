package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddWorkoutSetRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutSetRequest;
import com.myfitnesslog.entity.SetCategory;
import com.myfitnesslog.entity.WorkoutExercise;
import com.myfitnesslog.entity.WorkoutSet;
import com.myfitnesslog.entity.WorkoutStatus;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.repository.WorkoutExerciseRepository;
import com.myfitnesslog.repository.WorkoutSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Default {@link WorkoutSetService}. Enforces the workout-history rules via the
 * owning session's status.
 *
 * <p>Sets may be added, updated and deleted while the session is
 * {@code IN_PROGRESS}, which is ordinary logging, and also while it is
 * {@code COMPLETED}, which is a correction: a mistyped weight, a set marked
 * complete that was not finished, a fourth set that was performed but never
 * logged (ADR-0018).
 *
 * <p>A {@code DISCARDED} session is rejected with a {@link BusinessRuleException}
 * (409). Discarding is a deletion rather than a record, so there is nothing to
 * correct.
 *
 * <p>Note what is deliberately <em>not</em> relaxed here. The planning snapshot on
 * {@code WorkoutExercise} stays immutable once a session leaves
 * {@code IN_PROGRESS}, enforced in {@link WorkoutExerciseServiceImpl}. Correcting
 * what was performed is a different act from rewriting what was planned, and only
 * the first is permitted. That distinction is the whole of ADR-0004's guarantee.
 */
@Service
public class WorkoutSetServiceImpl implements WorkoutSetService {

    private final WorkoutSetRepository workoutSetRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;

    public WorkoutSetServiceImpl(
            WorkoutSetRepository workoutSetRepository,
            WorkoutExerciseRepository workoutExerciseRepository) {
        this.workoutSetRepository = workoutSetRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
    }

    @Override
    @Transactional
    public WorkoutSetSaveResult addSet(UUID workoutExerciseId, AddWorkoutSetRequest request) {
        WorkoutExercise workoutExercise = workoutExerciseRepository.findById(workoutExerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("Workout exercise not found."));
        requireEditable(workoutExercise);

        WorkoutSet existing = workoutSetRepository.findById(request.id()).orElse(null);
        if (existing != null) {
            requireEditable(existing.getWorkoutExercise());
            applyFields(existing, request.setNumber(), request.weight(), request.repetitions(),
                    request.setCategory(), request.startedAt(), request.finishedAt(),
                    request.rpe(), request.rir(), request.isCompleted());
            return new WorkoutSetSaveResult(workoutSetRepository.save(existing), false);
        }

        WorkoutSet workoutSet = new WorkoutSet();
        workoutSet.setId(request.id());
        workoutSet.setWorkoutExercise(workoutExercise);
        applyFields(workoutSet, request.setNumber(), request.weight(), request.repetitions(),
                request.setCategory(), request.startedAt(), request.finishedAt(),
                request.rpe(), request.rir(), request.isCompleted());
        return new WorkoutSetSaveResult(workoutSetRepository.save(workoutSet), true);
    }

    @Override
    @Transactional
    public WorkoutSet updateSet(UUID id, UpdateWorkoutSetRequest request) {
        WorkoutSet workoutSet = workoutSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workout set not found."));
        requireEditable(workoutSet.getWorkoutExercise());
        applyFields(workoutSet, request.setNumber(), request.weight(), request.repetitions(),
                request.setCategory(), request.startedAt(), request.finishedAt(),
                request.rpe(), request.rir(), request.isCompleted());
        return workoutSetRepository.save(workoutSet);
    }

    @Override
    @Transactional
    public void deleteSet(UUID id) {
        workoutSetRepository.findById(id).ifPresent(workoutSet -> {
            requireEditable(workoutSet.getWorkoutExercise());
            workoutSetRepository.delete(workoutSet);
        });
    }

    private void applyFields(
            WorkoutSet workoutSet,
            Integer setNumber,
            BigDecimal weight,
            Integer repetitions,
            SetCategory setCategory,
            Instant startedAt,
            Instant finishedAt,
            BigDecimal rpe,
            BigDecimal rir,
            Boolean isCompleted) {
        workoutSet.setSetNumber(setNumber);
        workoutSet.setWeight(weight);
        workoutSet.setRepetitions(repetitions);
        workoutSet.setSetCategory(setCategory);
        workoutSet.setStartedAt(startedAt);
        workoutSet.setFinishedAt(finishedAt);
        workoutSet.setRpe(rpe);
        workoutSet.setRir(rir);
        workoutSet.setCompleted(isCompleted);
    }

    /**
     * Permits set writes while a session is being logged, and afterwards as
     * corrections to what was performed (ADR-0018).
     *
     * <p>Written as an allow-list rather than by excluding {@code DISCARDED}, so
     * that a status added later is rejected until someone decides what it should
     * mean. Silently inheriting "editable" is how an immutability guarantee erodes.
     */
    private void requireEditable(WorkoutExercise workoutExercise) {
        WorkoutStatus status = workoutExercise.getWorkoutSession().getStatus();
        if (status != WorkoutStatus.IN_PROGRESS && status != WorkoutStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot modify a " + status.name().toLowerCase() + " workout.");
        }
    }
}
