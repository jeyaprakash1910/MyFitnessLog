package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddRoutineExerciseRequest;
import com.myfitnesslog.dto.request.CreateRoutineRequest;
import com.myfitnesslog.dto.request.ReorderRoutineExercisesRequest;
import com.myfitnesslog.dto.request.UpdateRoutineExerciseRequest;
import com.myfitnesslog.entity.RoutineExercise;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.RoutineExerciseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level business rules for routine exercises, against real PostgreSQL.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@Transactional
class RoutineExerciseServiceImplTest {

    @Autowired
    private RoutineExerciseService service;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private RoutineExerciseRepository routineExerciseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private UUID routineId;
    private UUID exerciseId;

    @BeforeEach
    void setUp() {
        routineId = routineService.createRoutine(
                new CreateRoutineRequest(UUID.randomUUID(), "Legs", null, 0)).routine().getId();
        exerciseId = exerciseRepository.findAll().get(0).getId();
    }

    private AddRoutineExerciseRequest addRequest(UUID id, int order) {
        return new AddRoutineExerciseRequest(id, exerciseId, order, 3, 8, 12, 90, "notes");
    }

    private UUID addExercise(int order) {
        return service.addExercise(routineId, addRequest(UUID.randomUUID(), order)).routineExercise().getId();
    }

    @Test
    void addCreatesWithReferences() {
        RoutineExerciseSaveResult result = service.addExercise(routineId, addRequest(UUID.randomUUID(), 0));

        assertThat(result.created()).isTrue();
        assertThat(result.routineExercise().getRoutine().getId()).isEqualTo(routineId);
        assertThat(result.routineExercise().getExercise().getId()).isEqualTo(exerciseId);
        assertThat(result.routineExercise().getTargetSets()).isEqualTo(3);
    }

    @Test
    void addIsIdempotentBySuppliedId() {
        UUID id = UUID.randomUUID();
        service.addExercise(routineId, new AddRoutineExerciseRequest(id, exerciseId, 0, 3, 8, 12, 90, "a"));

        RoutineExerciseSaveResult replay = service.addExercise(routineId,
                new AddRoutineExerciseRequest(id, exerciseId, 0, 4, 6, 10, 60, "b"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.routineExercise().getTargetSets()).isEqualTo(4);
        assertThat(routineExerciseRepository.findByRoutine_Id(routineId)).hasSize(1);
    }

    @Test
    void addRejectsUnknownRoutineOrExercise() {
        assertThatThrownBy(() -> service.addExercise(UUID.randomUUID(), addRequest(UUID.randomUUID(), 0)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.addExercise(routineId,
                new AddRoutineExerciseRequest(UUID.randomUUID(), UUID.randomUUID(), 0, 3, 8, 12, 90, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesFieldsAndRejectsUnknown() {
        UUID id = addExercise(0);

        RoutineExercise updated = service.updateExercise(id,
                new UpdateRoutineExerciseRequest(1, 5, 10, 15, 120, "changed"));
        assertThat(updated.getTargetSets()).isEqualTo(5);
        assertThat(updated.getExerciseOrder()).isEqualTo(1);

        assertThatThrownBy(() -> service.updateExercise(UUID.randomUUID(),
                new UpdateRoutineExerciseRequest(0, 3, 8, 12, 90, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesAndIsIdempotent() {
        UUID id = addExercise(0);

        service.deleteExercise(id);
        assertThat(routineExerciseRepository.findById(id)).isEmpty();
        // Second delete is a no-op (no exception).
        service.deleteExercise(id);
    }

    @Test
    void reorderAssignsOrderByIndex() {
        UUID first = addExercise(0);
        UUID second = addExercise(1);

        List<RoutineExercise> reordered = service.reorderExercises(routineId,
                new ReorderRoutineExercisesRequest(List.of(second, first)));

        assertThat(reordered).extracting(RoutineExercise::getId).containsExactly(second, first);
        assertThat(reordered).extracting(RoutineExercise::getExerciseOrder).containsExactly(0, 1);
    }

    @Test
    void reorderRejectsDuplicatesMismatchAndUnknownRoutine() {
        UUID first = addExercise(0);
        UUID second = addExercise(1);

        assertThatThrownBy(() -> service.reorderExercises(routineId,
                new ReorderRoutineExercisesRequest(List.of(first, first))))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> service.reorderExercises(routineId,
                new ReorderRoutineExercisesRequest(List.of(first))))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> service.reorderExercises(routineId,
                new ReorderRoutineExercisesRequest(List.of(first, second, UUID.randomUUID()))))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> service.reorderExercises(UUID.randomUUID(),
                new ReorderRoutineExercisesRequest(List.of(first, second))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
