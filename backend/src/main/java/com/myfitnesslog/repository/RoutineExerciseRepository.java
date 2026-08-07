package com.myfitnesslog.repository;

import com.myfitnesslog.entity.RoutineExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link RoutineExercise}. */
public interface RoutineExerciseRepository extends JpaRepository<RoutineExercise, UUID> {

    /** All exercises of a routine (unordered); used for reorder validation. */
    List<RoutineExercise> findByRoutine_Id(UUID routineId);

    /**
     * A routine's exercises in the order the user arranged them.
     *
     * <p>Ordering belongs in the query rather than in a caller's sort, because the
     * order <em>is</em> the routine: it is the sequence the exercises are performed
     * in, not a display preference. A caller that forgot to sort would silently
     * hand back a scrambled workout.
     */
    List<RoutineExercise> findByRoutine_IdOrderByExerciseOrderAsc(UUID routineId);
}
