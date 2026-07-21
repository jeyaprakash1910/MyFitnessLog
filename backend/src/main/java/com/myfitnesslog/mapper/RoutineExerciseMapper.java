package com.myfitnesslog.mapper;

import com.myfitnesslog.dto.response.RoutineExerciseResponse;
import com.myfitnesslog.entity.RoutineExercise;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps RoutineExercise entities to their response DTOs. exerciseId is taken from
 * the master exercise's identifier only; reading a lazy proxy's id does not
 * initialize it, so no query is issued and no LazyInitializationException occurs
 * (open-session-in-view is disabled). Conversion only; no business logic.
 */
@Mapper(componentModel = "spring")
public interface RoutineExerciseMapper {

    @Mapping(target = "exerciseId", source = "exercise.id")
    RoutineExerciseResponse toResponse(RoutineExercise routineExercise);

    List<RoutineExerciseResponse> toResponseList(List<RoutineExercise> routineExercises);
}
