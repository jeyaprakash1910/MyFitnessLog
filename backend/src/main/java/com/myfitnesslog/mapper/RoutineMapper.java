package com.myfitnesslog.mapper;

import com.myfitnesslog.dto.response.RoutineResponse;
import com.myfitnesslog.entity.Routine;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Maps Routine entities to their response DTOs. Conversion only; no business
 * logic (that lives in the service).
 */
@Mapper(componentModel = "spring")
public interface RoutineMapper {

    RoutineResponse toResponse(Routine routine);

    List<RoutineResponse> toResponseList(List<Routine> routines);
}
