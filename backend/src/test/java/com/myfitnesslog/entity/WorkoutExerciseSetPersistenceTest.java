package com.myfitnesslog.entity;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.WorkoutExerciseRepository;
import com.myfitnesslog.repository.WorkoutSessionRepository;
import com.myfitnesslog.repository.WorkoutSetRepository;
import com.myfitnesslog.support.JpaPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence for {@link WorkoutExercise} and {@link WorkoutSet}: snapshot
 * fields, BigDecimal precision round-trips, enum storage, the RPE CHECK
 * constraint, and the ON DELETE CASCADE chain from session to sets.
 */
@JpaPostgresTest
class WorkoutExerciseSetPersistenceTest {

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private WorkoutExerciseRepository workoutExerciseRepository;

    @Autowired
    private WorkoutSetRepository workoutSetRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private DefaultUserProvider defaultUserProvider;

    @Autowired
    private TestEntityManager entityManager;

    private WorkoutSession persistedSession() {
        WorkoutSession session = new WorkoutSession();
        session.setId(UUID.randomUUID());
        session.setUser(defaultUserProvider.getReference());
        session.setStartedAt(Instant.now());
        return sessionRepository.saveAndFlush(session);
    }

    private WorkoutExercise persistedExercise(WorkoutSession session) {
        Exercise master = exerciseRepository.findAll().get(0);
        WorkoutExercise we = new WorkoutExercise();
        we.setId(UUID.randomUUID());
        we.setWorkoutSession(session);
        we.setExercise(master);
        we.setExerciseName(master.getName());
        we.setExerciseOrder(0);
        we.setTargetSets(3);
        we.setMinTargetReps(8);
        we.setMaxTargetReps(12);
        return workoutExerciseRepository.saveAndFlush(we);
    }

    private WorkoutSet newSet(WorkoutExercise we, BigDecimal weight, BigDecimal rpe) {
        WorkoutSet set = new WorkoutSet();
        set.setId(UUID.randomUUID());
        set.setWorkoutExercise(we);
        set.setSetNumber(1);
        set.setWeight(weight);
        set.setRepetitions(8);
        set.setSetCategory(SetCategory.TOP_SET);
        set.setRpe(rpe);
        return set;
    }

    @Test
    void roundTripsBigDecimalPrecisionAndEnum() {
        WorkoutSession session = persistedSession();
        WorkoutExercise we = persistedExercise(session);
        WorkoutSet saved = workoutSetRepository.saveAndFlush(
                newSet(we, new BigDecimal("82.50"), new BigDecimal("8.5")));
        entityManager.clear();

        WorkoutSet found = workoutSetRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getWeight()).isEqualByComparingTo("82.50");
        assertThat(found.getWeight().scale()).isEqualTo(2);
        assertThat(found.getRpe()).isEqualByComparingTo("8.5");
        assertThat(found.getRpe().scale()).isEqualTo(1);
        assertThat(found.getSetCategory()).isEqualTo(SetCategory.TOP_SET);
        assertThat(found.isCompleted()).isTrue();
    }

    @Test
    void rejectsRpeOutOfRange() {
        WorkoutSession session = persistedSession();
        WorkoutExercise we = persistedExercise(session);
        assertThatThrownBy(() ->
                workoutSetRepository.saveAndFlush(newSet(we, new BigDecimal("80.00"), new BigDecimal("20.0"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingSessionCascadesToExercisesAndSets() {
        WorkoutSession session = persistedSession();
        WorkoutExercise we = persistedExercise(session);
        WorkoutSet set = workoutSetRepository.saveAndFlush(
                newSet(we, new BigDecimal("80.00"), null));
        UUID sessionId = session.getId();
        UUID setId = set.getId();
        // Detach the graph so deleting the session issues only a single session
        // DELETE; the DB ON DELETE CASCADE (not JPA) must remove the children.
        entityManager.flush();
        entityManager.clear();

        sessionRepository.delete(sessionRepository.findById(sessionId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        Number remaining = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM \"WorkoutSet\" WHERE \"id\" = :id")
                .setParameter("id", setId)
                .getSingleResult();
        assertThat(remaining.longValue()).isZero();
        assertThat(sessionRepository.findById(sessionId)).isEmpty();
    }
}
