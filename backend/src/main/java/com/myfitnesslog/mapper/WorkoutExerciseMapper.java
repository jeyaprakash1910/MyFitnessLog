package com.myfitnesslog.mapper;

import com.myfitnesslog.dto.response.WorkoutExerciseResponse;
import com.myfitnesslog.entity.WorkoutExercise;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps WorkoutExercise entities to their flat response DTOs. The parent session
 * and master exercise are taken by id from the lazy proxies (no initialization,
 * no LazyInitializationException). Conversion only; no business logic.
 */
@Mapper(componentModel = "spring")
public interface WorkoutExerciseMapper {

    @Mapping(target = "workoutSessionId", source = "workoutSession.id")
    @Mapping(target = "exerciseId", source = "exercise.id")
    WorkoutExerciseResponse toResponse(WorkoutExercise workoutExercise);
}
