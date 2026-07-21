package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddWorkoutExerciseRequest;
import com.myfitnesslog.dto.request.AddWorkoutSetRequest;
import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutSetRequest;
import com.myfitnesslog.entity.SetCategory;
import com.myfitnesslog.entity.WorkoutSet;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.WorkoutSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level rules for workout sets, against real PostgreSQL. Transactional:
 * each test rolls back.
 */
@SpringBootTest
@Transactional
class WorkoutSetServiceImplTest {

    @Autowired
    private WorkoutSetService service;

    @Autowired
    private WorkoutExerciseService exerciseService;

    @Autowired
    private WorkoutSessionService sessionService;

    @Autowired
    private WorkoutSetRepository workoutSetRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private UUID sessionId;
    private UUID workoutExerciseId;

    @BeforeEach
    void setUp() {
        sessionId = sessionService.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), null, Instant.parse("2026-07-20T09:00:00Z"), null))
                .session().getId();
        UUID exerciseId = exerciseRepository.findAll().get(0).getId();
        workoutExerciseId = exerciseService.addExercise(sessionId,
                new AddWorkoutExerciseRequest(UUID.randomUUID(), exerciseId, "Squat", 0, 3, 8, 12, 90, null))
                .workoutExercise().getId();
    }

    private AddWorkoutSetRequest addRequest(UUID id, int setNumber) {
        return new AddWorkoutSetRequest(id, setNumber, new BigDecimal("100.00"), 5,
                SetCategory.WORKING, null, null, new BigDecimal("8.5"), null, true);
    }

    private UUID addSet(int setNumber) {
        return service.addSet(workoutExerciseId, addRequest(UUID.randomUUID(), setNumber)).workoutSet().getId();
    }

    private void completeSession() {
        sessionService.completeWorkout(sessionId,
                new CompleteWorkoutSessionRequest(Instant.parse("2026-07-20T10:00:00Z"), null));
    }

    @Test
    void addPersistsMeasuredValues() {
        WorkoutSetSaveResult result = service.addSet(workoutExerciseId, addRequest(UUID.randomUUID(), 1));

        assertThat(result.created()).isTrue();
        assertThat(result.workoutSet().getWeight()).isEqualByComparingTo("100.00");
        assertThat(result.workoutSet().getRpe()).isEqualByComparingTo("8.5");
        assertThat(result.workoutSet().getSetCategory()).isEqualTo(SetCategory.WORKING);
    }

    @Test
    void addIsIdempotent() {
        UUID id = UUID.randomUUID();
        service.addSet(workoutExerciseId, addRequest(id, 1));
        WorkoutSetSaveResult replay = service.addSet(workoutExerciseId,
                new AddWorkoutSetRequest(id, 1, new BigDecimal("110.00"), 3, SetCategory.TOP_SET, null, null, null, null, true));

        assertThat(replay.created()).isFalse();
        assertThat(replay.workoutSet().getWeight()).isEqualByComparingTo("110.00");
        assertThat(workoutSetRepository.findByWorkoutExercise_IdOrderBySetNumberAsc(workoutExerciseId)).hasSize(1);
    }

    @Test
    void addRejectsUnknownExercise() {
        assertThatThrownBy(() -> service.addSet(UUID.randomUUID(), addRequest(UUID.randomUUID(), 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesValuesAndRejectsUnknown() {
        UUID id = addSet(1);
        WorkoutSet updated = service.updateSet(id,
                new UpdateWorkoutSetRequest(1, new BigDecimal("120.00"), 4, SetCategory.BACKOFF, null, null, null, null, false));
        assertThat(updated.getWeight()).isEqualByComparingTo("120.00");
        assertThat(updated.isCompleted()).isFalse();

        assertThatThrownBy(() -> service.updateSet(UUID.randomUUID(),
                new UpdateWorkoutSetRequest(1, BigDecimal.ZERO, 0, SetCategory.WORKING, null, null, null, null, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesAndIsIdempotent() {
        UUID id = addSet(1);
        service.deleteSet(id);
        assertThat(workoutSetRepository.findById(id)).isEmpty();
        service.deleteSet(id); // no-op
    }

    @Test
    void mutationsRejectedOnceSessionCompleted() {
        UUID id = addSet(1);
        completeSession();

        assertThatThrownBy(() -> service.addSet(workoutExerciseId, addRequest(UUID.randomUUID(), 2)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.updateSet(id,
                new UpdateWorkoutSetRequest(1, BigDecimal.TEN, 5, SetCategory.WORKING, null, null, null, null, true)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.deleteSet(id))
                .isInstanceOf(BusinessRuleException.class);
    }
}
