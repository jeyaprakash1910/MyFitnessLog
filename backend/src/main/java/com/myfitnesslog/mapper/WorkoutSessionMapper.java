package com.myfitnesslog.mapper;

import com.myfitnesslog.dto.response.WorkoutSessionResponse;
import com.myfitnesslog.entity.WorkoutSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps WorkoutSession entities to their response DTOs. routineId is taken from the
 * (nullable) routine's identifier only; reading a lazy proxy's id does not
 * initialize it, so no query is issued and no LazyInitializationException occurs
 * (open-session-in-view is disabled). status maps to the enum name. Conversion
 * only; no business logic.
 */
@Mapper(componentModel = "spring")
public interface WorkoutSessionMapper {

    @Mapping(target = "routineId", source = "routine.id")
    WorkoutSessionResponse toResponse(WorkoutSession session);

    List<WorkoutSessionResponse> toResponseList(List<WorkoutSession> sessions);
}
