package com.myfitnesslog.repository;

import com.myfitnesslog.entity.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link WorkoutSet}. */
public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {

    /** An exercise's sets in set-number order; used to assemble the workout detail. */
    List<WorkoutSet> findByWorkoutExercise_IdOrderBySetNumberAsc(UUID workoutExerciseId);
}
