package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddWorkoutExerciseRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutExerciseRequest;
import com.myfitnesslog.entity.Exercise;
import com.myfitnesslog.entity.WorkoutExercise;
import com.myfitnesslog.entity.WorkoutSession;
import com.myfitnesslog.entity.WorkoutStatus;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.WorkoutExerciseRepository;
import com.myfitnesslog.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Default {@link WorkoutExerciseService}. Enforces the workout-history
 * immutability rule: exercises may be added/updated/deleted only while the parent
 * session is IN_PROGRESS; otherwise a {@link BusinessRuleException} (409) is thrown.
 */
@Service
public class WorkoutExerciseServiceImpl implements WorkoutExerciseService {

    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final ExerciseRepository exerciseRepository;

    public WorkoutExerciseServiceImpl(
            WorkoutExerciseRepository workoutExerciseRepository,
            WorkoutSessionRepository sessionRepository,
            ExerciseRepository exerciseRepository) {
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.sessionRepository = sessionRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    @Transactional
    public WorkoutExerciseSaveResult addExercise(UUID sessionId, AddWorkoutExerciseRequest request) {
        WorkoutSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Workout session not found."));
        requireInProgress(session);

        WorkoutExercise existing = workoutExerciseRepository.findById(request.id()).orElse(null);
        if (existing != null) {
            requireInProgress(existing.getWorkoutSession());
            applyFields(existing, request);
            return new WorkoutExerciseSaveResult(workoutExerciseRepository.save(existing), false);
        }

        Exercise exercise = exerciseRepository.findById(request.exerciseId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found."));

        WorkoutExercise workoutExercise = new WorkoutExercise();
        workoutExercise.setId(request.id());
        workoutExercise.setWorkoutSession(session);
        workoutExercise.setExercise(exercise);
        applyFields(workoutExercise, request);
        return new WorkoutExerciseSaveResult(workoutExerciseRepository.save(workoutExercise), true);
    }

    @Override
    @Transactional
    public WorkoutExercise updateExercise(UUID id, UpdateWorkoutExerciseRequest request) {
        WorkoutExercise workoutExercise = workoutExerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workout exercise not found."));
        requireInProgress(workoutExercise.getWorkoutSession());
        workoutExercise.setExerciseName(request.exerciseName());
        workoutExercise.setExerciseOrder(request.exerciseOrder());
        workoutExercise.setTargetSets(request.targetSets());
        workoutExercise.setMinTargetReps(request.minTargetReps());
        workoutExercise.setMaxTargetReps(request.maxTargetReps());
        workoutExercise.setTargetRestSeconds(request.targetRestSeconds());
        workoutExercise.setNotes(request.notes());
        return workoutExerciseRepository.save(workoutExercise);
    }

    @Override
    @Transactional
    public void deleteExercise(UUID id) {
        workoutExerciseRepository.findById(id).ifPresent(workoutExercise -> {
            requireInProgress(workoutExercise.getWorkoutSession());
            workoutExerciseRepository.delete(workoutExercise);
        });
    }

    private void applyFields(WorkoutExercise workoutExercise, AddWorkoutExerciseRequest request) {
        workoutExercise.setExerciseName(request.exerciseName());
        workoutExercise.setExerciseOrder(request.exerciseOrder());
        workoutExercise.setTargetSets(request.targetSets());
        workoutExercise.setMinTargetReps(request.minTargetReps());
        workoutExercise.setMaxTargetReps(request.maxTargetReps());
        workoutExercise.setTargetRestSeconds(request.targetRestSeconds());
        workoutExercise.setNotes(request.notes());
    }

    private void requireInProgress(WorkoutSession session) {
        if (session.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "Cannot modify a " + session.getStatus().name().toLowerCase() + " workout.");
        }
    }
}
