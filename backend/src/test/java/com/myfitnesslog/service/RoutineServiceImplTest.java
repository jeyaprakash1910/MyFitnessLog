package com.myfitnesslog.service;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.dto.request.CreateRoutineRequest;
import com.myfitnesslog.dto.request.UpdateRoutineRequest;
import com.myfitnesslog.entity.Routine;
import com.myfitnesslog.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level business rules for routines, exercised against the real
 * PostgreSQL test database. Transactional: each test rolls back.
 */
@SpringBootTest
@Transactional
class RoutineServiceImplTest {

    @Autowired
    private RoutineService routineService;

    private CreateRoutineRequest createRequest(String name) {
        return new CreateRoutineRequest(UUID.randomUUID(), name, "desc", 0);
    }

    @Test
    void createAttachesDefaultUserAndReportsCreated() {
        RoutineSaveResult result = routineService.createRoutine(createRequest("Push A"));

        assertThat(result.created()).isTrue();
        assertThat(result.routine().getUser().getId()).isEqualTo(DefaultUserProvider.DEFAULT_USER_ID);
        assertThat(result.routine().getCreatedAt()).isNotNull();
    }

    @Test
    void createIsIdempotentBySuppliedId() {
        UUID id = UUID.randomUUID();
        routineService.createRoutine(new CreateRoutineRequest(id, "Original", null, 0));

        RoutineSaveResult replay = routineService.createRoutine(
                new CreateRoutineRequest(id, "Updated", null, 1));

        assertThat(replay.created()).isFalse();
        assertThat(replay.routine().getName()).isEqualTo("Updated");
        assertThat(routineService.getAllRoutines()).hasSize(1);
    }

    @Test
    void softDeleteHidesRoutineFromReads() {
        RoutineSaveResult created = routineService.createRoutine(createRequest("Legs"));
        UUID id = created.routine().getId();

        routineService.deleteRoutine(id);

        assertThat(routineService.getAllRoutines()).noneMatch(r -> r.getId().equals(id));
        assertThatThrownBy(() -> routineService.getRoutine(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteIsIdempotentAndRejectsUnknown() {
        RoutineSaveResult created = routineService.createRoutine(createRequest("Legs"));
        routineService.deleteRoutine(created.routine().getId());
        // Second delete of an already-deleted routine is a no-op (no exception).
        routineService.deleteRoutine(created.routine().getId());

        assertThatThrownBy(() -> routineService.deleteRoutine(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesMutableFieldsButRejectsDeleted() {
        RoutineSaveResult created = routineService.createRoutine(createRequest("Pull"));
        UUID id = created.routine().getId();

        Routine updated = routineService.updateRoutine(id, new UpdateRoutineRequest("Pull B", "notes", 2));
        assertThat(updated.getName()).isEqualTo("Pull B");
        assertThat(updated.getDisplayOrder()).isEqualTo(2);

        routineService.deleteRoutine(id);
        assertThatThrownBy(() -> routineService.updateRoutine(id, new UpdateRoutineRequest("X", null, 0)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateProducesIndependentCopy() {
        RoutineSaveResult original = routineService.createRoutine(createRequest("Full Body"));
        UUID originalId = original.routine().getId();

        Routine copy = routineService.duplicateRoutine(originalId);

        assertThat(copy.getId()).isNotEqualTo(originalId);
        assertThat(copy.getName()).isEqualTo("Full Body (copy)");
        assertThat(routineService.getAllRoutines()).hasSize(2);

        // Mutating the copy must not affect the original.
        routineService.updateRoutine(copy.getId(), new UpdateRoutineRequest("Changed", null, 9));
        assertThat(routineService.getRoutine(originalId).getName()).isEqualTo("Full Body");
    }

    @Test
    void duplicateRejectsUnknown() {
        assertThatThrownBy(() -> routineService.duplicateRoutine(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
