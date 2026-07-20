package com.myfitnesslog.mapper;

import com.myfitnesslog.dto.response.ExerciseCategoryResponse;
import com.myfitnesslog.entity.ExerciseCategory;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Maps ExerciseCategory entities to their response DTOs.
 * Conversion only; no business logic.
 */
@Mapper(componentModel = "spring")
public interface ExerciseCategoryMapper {

    ExerciseCategoryResponse toResponse(ExerciseCategory category);

    List<ExerciseCategoryResponse> toResponseList(List<ExerciseCategory> categories);
}
