package com.myfitnesslog.service;

import com.myfitnesslog.dto.request.AddWorkoutExerciseRequest;
import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.dto.request.UpdateWorkoutExerciseRequest;
import com.myfitnesslog.entity.WorkoutExercise;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.WorkoutExerciseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level rules for workout exercises, against real PostgreSQL.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@Transactional
class WorkoutExerciseServiceImplTest {

    @Autowired
    private WorkoutExerciseService service;

    @Autowired
    private WorkoutSessionService sessionService;

    @Autowired
    private WorkoutExerciseRepository workoutExerciseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private UUID sessionId;
    private UUID exerciseId;

    @BeforeEach
    void setUp() {
        sessionId = sessionService.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), null, null, Instant.parse("2026-07-20T09:00:00Z"), null))
                .session().getId();
        exerciseId = exerciseRepository.findAll().get(0).getId();
    }

    private AddWorkoutExerciseRequest addRequest(UUID id) {
        return new AddWorkoutExerciseRequest(id, exerciseId, "Squat", 0, 3, 8, 12, 90, "n");
    }

    private UUID addExercise() {
        return service.addExercise(sessionId, addRequest(UUID.randomUUID())).workoutExercise().getId();
    }

    private void completeSession() {
        sessionService.completeWorkout(sessionId,
                new CompleteWorkoutSessionRequest(Instant.parse("2026-07-20T10:00:00Z"), null));
    }

    @Test
    void addSnapshotsNameAndReferences() {
        WorkoutExerciseSaveResult result = service.addExercise(sessionId, addRequest(UUID.randomUUID()));

        assertThat(result.created()).isTrue();
        assertThat(result.workoutExercise().getExerciseName()).isEqualTo("Squat");
        assertThat(result.workoutExercise().getExercise().getId()).isEqualTo(exerciseId);
        assertThat(result.workoutExercise().getWorkoutSession().getId()).isEqualTo(sessionId);
    }

    @Test
    void addIsIdempotent() {
        UUID id = UUID.randomUUID();
        service.addExercise(sessionId, addRequest(id));
        WorkoutExerciseSaveResult replay = service.addExercise(sessionId,
                new AddWorkoutExerciseRequest(id, exerciseId, "Squat", 0, 5, 8, 12, 90, "n2"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.workoutExercise().getTargetSets()).isEqualTo(5);
        assertThat(workoutExerciseRepository.findByWorkoutSession_IdOrderByExerciseOrderAsc(sessionId)).hasSize(1);
    }

    @Test
    void addRejectsUnknownSessionAndExercise() {
        assertThatThrownBy(() -> service.addExercise(UUID.randomUUID(), addRequest(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.addExercise(sessionId,
                new AddWorkoutExerciseRequest(UUID.randomUUID(), UUID.randomUUID(), "X", 0, 3, 8, 12, 90, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesSnapshotFieldsAndRejectsUnknown() {
        UUID id = addExercise();
        WorkoutExercise updated = service.updateExercise(id,
                new UpdateWorkoutExerciseRequest("Front Squat", 1, 4, 6, 10, 120, "x"));
        assertThat(updated.getExerciseName()).isEqualTo("Front Squat");
        assertThat(updated.getTargetSets()).isEqualTo(4);

        assertThatThrownBy(() -> service.updateExercise(UUID.randomUUID(),
                new UpdateWorkoutExerciseRequest("X", 0, 3, 8, 12, 90, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesAndIsIdempotent() {
        UUID id = addExercise();
        service.deleteExercise(id);
        assertThat(workoutExerciseRepository.findById(id)).isEmpty();
        service.deleteExercise(id); // no-op
    }

    @Test
    void mutationsRejectedOnceSessionCompleted() {
        UUID id = addExercise();
        completeSession();

        assertThatThrownBy(() -> service.addExercise(sessionId, addRequest(UUID.randomUUID())))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.updateExercise(id,
                new UpdateWorkoutExerciseRequest("X", 0, 3, 8, 12, 90, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.deleteExercise(id))
                .isInstanceOf(BusinessRuleException.class);
    }
}
