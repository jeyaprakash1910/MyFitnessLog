package com.myfitnesslog.repository;

import com.myfitnesslog.entity.RoutineExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link RoutineExercise}. */
public interface RoutineExerciseRepository extends JpaRepository<RoutineExercise, UUID> {

    /** All exercises of a routine (unordered); used for reorder validation. */
    List<RoutineExercise> findByRoutine_Id(UUID routineId);
}
