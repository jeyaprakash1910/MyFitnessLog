package com.myfitnesslog.mapper;

import com.myfitnesslog.dto.response.WorkoutSetResponse;
import com.myfitnesslog.entity.WorkoutSet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps WorkoutSet entities to their response DTOs. The parent workout exercise is
 * taken by id from the lazy proxy (no initialization). setCategory maps to the
 * enum name. Conversion only; no business logic.
 */
@Mapper(componentModel = "spring")
public interface WorkoutSetMapper {

    @Mapping(target = "workoutExerciseId", source = "workoutExercise.id")
    @Mapping(target = "isCompleted", source = "completed")
    WorkoutSetResponse toResponse(WorkoutSet workoutSet);
}
