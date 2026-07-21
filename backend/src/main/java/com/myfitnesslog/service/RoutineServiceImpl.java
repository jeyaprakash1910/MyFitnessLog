package com.myfitnesslog.service;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.dto.request.CreateRoutineRequest;
import com.myfitnesslog.dto.request.UpdateRoutineRequest;
import com.myfitnesslog.entity.Routine;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.repository.RoutineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link RoutineService}. Applies the documented business rules against
 * the repository; soft-deleted routines are filtered/ordered in the service (the
 * same in-memory approach used for exercises while the data set is small).
 */
@Service
public class RoutineServiceImpl implements RoutineService {

    private final RoutineRepository routineRepository;
    private final DefaultUserProvider defaultUserProvider;

    public RoutineServiceImpl(
            RoutineRepository routineRepository,
            DefaultUserProvider defaultUserProvider) {
        this.routineRepository = routineRepository;
        this.defaultUserProvider = defaultUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Routine> getAllRoutines() {
        return routineRepository.findAll().stream()
                .filter(routine -> !routine.isDeleted())
                .sorted(Comparator.comparingInt(Routine::getDisplayOrder))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Routine getRoutine(UUID id) {
        return findActive(id);
    }

    @Override
    @Transactional
    public RoutineSaveResult createRoutine(CreateRoutineRequest request) {
        Routine existing = routineRepository.findById(request.id()).orElse(null);
        if (existing != null) {
            // Idempotent replay: converge the existing routine to the submitted
            // state without creating a duplicate. isDeleted is left untouched.
            applyFields(existing, request.name(), request.description(), request.displayOrder());
            return new RoutineSaveResult(routineRepository.save(existing), false);
        }
        Routine routine = new Routine();
        routine.setId(request.id());
        routine.setUser(defaultUserProvider.getReference());
        applyFields(routine, request.name(), request.description(), request.displayOrder());
        return new RoutineSaveResult(routineRepository.save(routine), true);
    }

    @Override
    @Transactional
    public Routine updateRoutine(UUID id, UpdateRoutineRequest request) {
        Routine routine = findActive(id);
        applyFields(routine, request.name(), request.description(), request.displayOrder());
        return routineRepository.save(routine);
    }

    @Override
    @Transactional
    public void deleteRoutine(UUID id) {
        Routine routine = routineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routine not found."));
        if (!routine.isDeleted()) {
            routine.setDeleted(true);
            routineRepository.save(routine);
        }
    }

    @Override
    @Transactional
    public Routine duplicateRoutine(UUID id) {
        Routine original = findActive(id);
        Routine copy = new Routine();
        copy.setId(UUID.randomUUID());
        copy.setUser(defaultUserProvider.getReference());
        copy.setName(original.getName() + " (copy)");
        copy.setDescription(original.getDescription());
        copy.setDisplayOrder(original.getDisplayOrder());
        return routineRepository.save(copy);
    }

    private Routine findActive(UUID id) {
        Routine routine = routineRepository.findById(id)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Routine not found."));
        return routine;
    }

    private void applyFields(Routine routine, String name, String description, Integer displayOrder) {
        routine.setName(name);
        routine.setDescription(description);
        routine.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
    }
}
