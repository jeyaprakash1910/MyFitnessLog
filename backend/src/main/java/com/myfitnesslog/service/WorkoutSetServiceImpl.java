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
 * Default {@link WorkoutSetService}. Enforces the workout-history immutability
 * rule via the owning session's status: sets may be added/updated/deleted only
 * while that session is IN_PROGRESS; otherwise a {@link BusinessRuleException}
 * (409) is thrown.
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
        requireInProgress(workoutExercise);

        WorkoutSet existing = workoutSetRepository.findById(request.id()).orElse(null);
        if (existing != null) {
            requireInProgress(existing.getWorkoutExercise());
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
        requireInProgress(workoutSet.getWorkoutExercise());
        applyFields(workoutSet, request.setNumber(), request.weight(), request.repetitions(),
                request.setCategory(), request.startedAt(), request.finishedAt(),
                request.rpe(), request.rir(), request.isCompleted());
        return workoutSetRepository.save(workoutSet);
    }

    @Override
    @Transactional
    public void deleteSet(UUID id) {
        workoutSetRepository.findById(id).ifPresent(workoutSet -> {
            requireInProgress(workoutSet.getWorkoutExercise());
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

    private void requireInProgress(WorkoutExercise workoutExercise) {
        WorkoutStatus status = workoutExercise.getWorkoutSession().getStatus();
        if (status != WorkoutStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Cannot modify a " + status.name().toLowerCase() + " workout.");
        }
    }
}
