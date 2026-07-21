package com.myfitnesslog.entity;

import com.myfitnesslog.config.DefaultUserProvider;
import com.myfitnesslog.repository.ExerciseRepository;
import com.myfitnesslog.repository.RoutineExerciseRepository;
import com.myfitnesslog.repository.RoutineRepository;
import com.myfitnesslog.support.JpaPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence for {@link RoutineExercise}: the FKs to routine and master
 * exercise, and the DB CHECK constraints (targetSets &gt; 0, reps ordering).
 */
@JpaPostgresTest
class RoutineExercisePersistenceTest {

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private RoutineExerciseRepository routineExerciseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private DefaultUserProvider defaultUserProvider;

    private Routine persistedRoutine() {
        Routine routine = new Routine();
        routine.setId(UUID.randomUUID());
        routine.setUser(defaultUserProvider.getReference());
        routine.setName("Routine");
        return routineRepository.saveAndFlush(routine);
    }

    private RoutineExercise newRoutineExercise(Routine routine, int targetSets, int minReps, int maxReps) {
        // Reuse a seeded master exercise (V3 seeds 40) as the FK target.
        Exercise exercise = exerciseRepository.findAll().get(0);
        RoutineExercise re = new RoutineExercise();
        re.setId(UUID.randomUUID());
        re.setRoutine(routine);
        re.setExercise(exercise);
        re.setExerciseOrder(0);
        re.setTargetSets(targetSets);
        re.setMinTargetReps(minReps);
        re.setMaxTargetReps(maxReps);
        re.setTargetRestSeconds(90);
        return re;
    }

    @Test
    void persistsWithValidTargets() {
        Routine routine = persistedRoutine();
        RoutineExercise saved = routineExerciseRepository.saveAndFlush(
                newRoutineExercise(routine, 3, 8, 12));

        RoutineExercise found = routineExerciseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getRoutine().getId()).isEqualTo(routine.getId());
        assertThat(found.getExercise().getId()).isNotNull();
        assertThat(found.getTargetSets()).isEqualTo(3);
        assertThat(found.getTargetRestSeconds()).isEqualTo(90);
    }

    @Test
    void rejectsNonPositiveTargetSets() {
        Routine routine = persistedRoutine();
        assertThatThrownBy(() ->
                routineExerciseRepository.saveAndFlush(newRoutineExercise(routine, 0, 8, 12)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMaxRepsBelowMinReps() {
        Routine routine = persistedRoutine();
        assertThatThrownBy(() ->
                routineExerciseRepository.saveAndFlush(newRoutineExercise(routine, 3, 12, 8)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
