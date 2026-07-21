package com.myfitnesslog.repository;

import com.myfitnesslog.entity.WorkoutExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link WorkoutExercise}. */
public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, UUID> {

    /** A session's exercises in performed order; used to assemble the workout detail. */
    List<WorkoutExercise> findByWorkoutSession_IdOrderByExerciseOrderAsc(UUID workoutSessionId);
}
