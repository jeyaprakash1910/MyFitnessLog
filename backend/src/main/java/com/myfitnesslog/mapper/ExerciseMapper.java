package com.myfitnesslog.mapper;

import com.myfitnesslog.dto.response.ExerciseResponse;
import com.myfitnesslog.entity.Exercise;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps Exercise entities to their response DTOs.
 * categoryId is taken from the category's identifier only; accessing the
 * identifier of a lazy proxy does not initialize it, so no extra query is
 * issued and no LazyInitializationException occurs. Conversion only; no
 * business logic.
 */
@Mapper(componentModel = "spring")
public interface ExerciseMapper {

    @Mapping(target = "categoryId", source = "category.id")
    ExerciseResponse toResponse(Exercise exercise);

    List<ExerciseResponse> toResponseList(List<Exercise> exercises);
}
