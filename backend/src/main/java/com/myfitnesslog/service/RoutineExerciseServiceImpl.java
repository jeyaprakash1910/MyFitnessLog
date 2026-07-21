package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddRoutineExerciseRequest;
import com.myfitnesslog.dto.request.ReorderRoutineExercisesRequest;
import com.myfitnesslog.dto.request.UpdateRoutineExerciseRequest;
import com.myfitnesslog.entity.Exercise;
import com.myfitnesslog.entity.Routine;
import com.myfitnesslog.entity.RoutineExercise;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.RoutineExerciseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link RoutineExerciseService}. Enforces parent/exercise existence and
 * atomic reordering against the repositories. Routine existence is delegated to
 * {@link RoutineService#getRoutine} so the "active routine" rule is defined once.
 */
@Service
public class RoutineExerciseServiceImpl implements RoutineExerciseService {

    private final RoutineExerciseRepository routineExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final RoutineService routineService;

    public RoutineExerciseServiceImpl(
            RoutineExerciseRepository routineExerciseRepository,
            ExerciseRepository exerciseRepository,
            RoutineService routineService) {
        this.routineExerciseRepository = routineExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.routineService = routineService;
    }

    @Override
    @Transactional
    public RoutineExerciseSaveResult addExercise(UUID routineId, AddRoutineExerciseRequest request) {
        Routine routine = routineService.getRoutine(routineId);

        RoutineExercise existing = routineExerciseRepository.findById(request.id()).orElse(null);
        if (existing != null) {
            applyFields(existing, request.exerciseOrder(), request.targetSets(), request.minTargetReps(),
                    request.maxTargetReps(), request.targetRestSeconds(), request.notes());
            return new RoutineExerciseSaveResult(routineExerciseRepository.save(existing), false);
        }

        Exercise exercise = exerciseRepository.findById(request.exerciseId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found."));

        RoutineExercise routineExercise = new RoutineExercise();
        routineExercise.setId(request.id());
        routineExercise.setRoutine(routine);
        routineExercise.setExercise(exercise);
        applyFields(routineExercise, request.exerciseOrder(), request.targetSets(), request.minTargetReps(),
                request.maxTargetReps(), request.targetRestSeconds(), request.notes());
        return new RoutineExerciseSaveResult(routineExerciseRepository.save(routineExercise), true);
    }

    @Override
    @Transactional
    public RoutineExercise updateExercise(UUID id, UpdateRoutineExerciseRequest request) {
        RoutineExercise routineExercise = routineExerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routine exercise not found."));
        applyFields(routineExercise, request.exerciseOrder(), request.targetSets(), request.minTargetReps(),
                request.maxTargetReps(), request.targetRestSeconds(), request.notes());
        return routineExerciseRepository.save(routineExercise);
    }

    @Override
    @Transactional
    public void deleteExercise(UUID id) {
        routineExerciseRepository.findById(id).ifPresent(routineExerciseRepository::delete);
    }

    @Override
    @Transactional
    public List<RoutineExercise> reorderExercises(UUID routineId, ReorderRoutineExercisesRequest request) {
        routineService.getRoutine(routineId);
        List<UUID> orderedIds = request.orderedIds();

        Set<UUID> uniqueIds = new HashSet<>(orderedIds);
        if (uniqueIds.size() != orderedIds.size()) {
            throw new BusinessRuleException("Reorder list contains duplicate ids.");
        }

        Map<UUID, RoutineExercise> byId = new LinkedHashMap<>();
        for (RoutineExercise routineExercise : routineExerciseRepository.findByRoutine_Id(routineId)) {
            byId.put(routineExercise.getId(), routineExercise);
        }
        if (!uniqueIds.equals(byId.keySet())) {
            throw new BusinessRuleException("Reorder must list exactly the routine's exercises.");
        }

        for (int index = 0; index < orderedIds.size(); index++) {
            byId.get(orderedIds.get(index)).setExerciseOrder(index);
        }
        routineExerciseRepository.saveAll(byId.values());

        return byId.values().stream()
                .sorted(Comparator.comparingInt(RoutineExercise::getExerciseOrder))
                .toList();
    }

    private void applyFields(
            RoutineExercise routineExercise,
            Integer exerciseOrder,
            Integer targetSets,
            Integer minTargetReps,
            Integer maxTargetReps,
            Integer targetRestSeconds,
            String notes) {
        routineExercise.setExerciseOrder(exerciseOrder);
        routineExercise.setTargetSets(targetSets);
        routineExercise.setMinTargetReps(minTargetReps);
        routineExercise.setMaxTargetReps(maxTargetReps);
        routineExercise.setTargetRestSeconds(targetRestSeconds);
        routineExercise.setNotes(notes);
    }
}
