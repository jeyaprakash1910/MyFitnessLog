package com.myfitnesslog.service;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.dto.request.CompleteWorkoutSessionRequest;
import com.myfitnesslog.dto.request.CreateRoutineRequest;
import com.myfitnesslog.dto.request.DiscardWorkoutSessionRequest;
import com.myfitnesslog.dto.request.StartWorkoutSessionRequest;
import com.myfitnesslog.entity.WorkoutSession;
import com.myfitnesslog.entity.WorkoutStatus;
import com.myfitnesslog.exception.BusinessRuleException;
import com.myfitnesslog.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level lifecycle rules for workout sessions, against real PostgreSQL.
 * Transactional: each test rolls back.
 */
@SpringBootTest
@Transactional
class WorkoutSessionServiceImplTest {

    @Autowired
    private WorkoutSessionService service;

    @Autowired
    private RoutineService routineService;

    private final Instant startedAt = Instant.parse("2026-07-20T09:00:00Z");
    private final Instant endedAt = Instant.parse("2026-07-20T10:00:00Z");

    private WorkoutSessionSaveResult startManual(UUID id) {
        return service.startWorkout(new StartWorkoutSessionRequest(id, null, null, startedAt, "manual"));
    }

    @Test
    void startManualAttachesDefaultUserAndPreservesTimestamp() {
        WorkoutSessionSaveResult result = startManual(UUID.randomUUID());

        assertThat(result.created()).isTrue();
        assertThat(result.session().getRoutine()).isNull();
        assertThat(result.session().getStatus()).isEqualTo(WorkoutStatus.IN_PROGRESS);
        assertThat(result.session().getUser().getId()).isEqualTo(DefaultUserProvider.DEFAULT_USER_ID);
        assertThat(result.session().getStartedAt()).isEqualTo(startedAt);
    }

    @Test
    void startFromRoutineLinksRoutineAndRejectsUnknown() {
        UUID routineId = routineService.createRoutine(
                new CreateRoutineRequest(UUID.randomUUID(), "Legs", null, 0)).routine().getId();

        WorkoutSessionSaveResult result = service.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), routineId, null, startedAt, null));
        assertThat(result.session().getRoutine().getId()).isEqualTo(routineId);

        assertThatThrownBy(() -> service.startWorkout(
                new StartWorkoutSessionRequest(UUID.randomUUID(), UUID.randomUUID(), null, startedAt, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startIsIdempotentAndDoesNotReopenFinishedWorkout() {
        UUID id = UUID.randomUUID();
        startManual(id);
        service.completeWorkout(id, new CompleteWorkoutSessionRequest(endedAt, null));

        WorkoutSessionSaveResult replay = service.startWorkout(
                new StartWorkoutSessionRequest(id, null, null, startedAt.plus(1, ChronoUnit.HOURS), "again"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.session().getStatus()).isEqualTo(WorkoutStatus.COMPLETED); // not reopened
    }

    @Test
    void completeTransitionsAndPreservesEndedAt() {
        UUID id = UUID.randomUUID();
        startManual(id);

        WorkoutSession completed = service.completeWorkout(id, new CompleteWorkoutSessionRequest(endedAt, "done"));
        assertThat(completed.getStatus()).isEqualTo(WorkoutStatus.COMPLETED);
        assertThat(completed.getEndedAt()).isEqualTo(endedAt);
        assertThat(completed.getNotes()).isEqualTo("done");
    }

    @Test
    void completeIsIdempotentAndRejectsDiscarded() {
        UUID id = UUID.randomUUID();
        startManual(id);
        service.completeWorkout(id, new CompleteWorkoutSessionRequest(endedAt, null));
        // Replay: no exception, no mutation.
        WorkoutSession replay = service.completeWorkout(id, new CompleteWorkoutSessionRequest(endedAt.plusSeconds(60), null));
        assertThat(replay.getEndedAt()).isEqualTo(endedAt);

        UUID discardedId = UUID.randomUUID();
        startManual(discardedId);
        service.discardWorkout(discardedId, new DiscardWorkoutSessionRequest(endedAt, null));
        assertThatThrownBy(() -> service.completeWorkout(discardedId, new CompleteWorkoutSessionRequest(endedAt, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void discardIsIdempotentAndAcceptsACompletedWorkout() {
        UUID id = UUID.randomUUID();
        startManual(id);
        service.discardWorkout(id, new DiscardWorkoutSessionRequest(endedAt, null));
        service.discardWorkout(id, new DiscardWorkoutSessionRequest(endedAt.plusSeconds(60), null)); // no-op

        // COMPLETED -> DISCARDED became legal on 2026-08-13: a workout that happened
        // but should not be in the record leaves it this way. endedAt is untouched,
        // because discarding afterwards does not change when training stopped.
        UUID completedId = UUID.randomUUID();
        startManual(completedId);
        service.completeWorkout(completedId, new CompleteWorkoutSessionRequest(endedAt, null));

        service.discardWorkout(completedId, new DiscardWorkoutSessionRequest(endedAt.plusSeconds(3600), null));

        WorkoutSession discarded = service.getWorkout(completedId);
        assertThat(discarded.getStatus()).isEqualTo(WorkoutStatus.DISCARDED);
        assertThat(discarded.getEndedAt()).isEqualTo(endedAt);

        // Terminal in both directions: a discarded workout cannot be completed.
        assertThatThrownBy(() -> service.completeWorkout(completedId, new CompleteWorkoutSessionRequest(endedAt, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void historyReturnsCompletedOnlyNewestFirst() {
        UUID older = UUID.randomUUID();
        service.startWorkout(new StartWorkoutSessionRequest(older, null, null, Instant.parse("2026-07-19T09:00:00Z"), null));
        service.completeWorkout(older, new CompleteWorkoutSessionRequest(endedAt, null));

        UUID newer = UUID.randomUUID();
        service.startWorkout(new StartWorkoutSessionRequest(newer, null, null, Instant.parse("2026-07-21T09:00:00Z"), null));
        service.completeWorkout(newer, new CompleteWorkoutSessionRequest(endedAt, null));

        // Neither an abandoned attempt nor a workout still being logged is history.
        UUID discarded = UUID.randomUUID();
        service.startWorkout(new StartWorkoutSessionRequest(discarded, null, null, Instant.parse("2026-07-20T09:00:00Z"), null));
        service.discardWorkout(discarded, new DiscardWorkoutSessionRequest(endedAt, null));

        UUID active = UUID.randomUUID();
        startManual(active);

        var history = service.getHistory();
        assertThat(history).extracting(WorkoutSession::getId).containsExactly(newer, older);
    }

    @Test
    void getWorkoutRejectsUnknown() {
        assertThatThrownBy(() -> service.getWorkout(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
