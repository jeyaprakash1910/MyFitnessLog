package com.myfitnesslog.feature.exercise.data.remote

import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import java.util.UUID

/**
 * Hand-written DTO → Entity mappers.
 *
 * Explicit mapping is preferred over a mapping library on Android: the
 * conversions are trivial, and being explicit matches CODING_STANDARDS
 * ("explicit over implicit"). Mapping lives with the network layer and is used
 * only inside repositories, so the rest of the app never sees DTOs.
 */
fun ExerciseCategoryDto.toEntity(): ExerciseCategoryEntity =
    ExerciseCategoryEntity(
        id = UUID.fromString(id),
        name = name,
    )

fun ExerciseDto.toEntity(): ExerciseEntity =
    ExerciseEntity(
        id = UUID.fromString(id),
        categoryId = UUID.fromString(categoryId),
        name = name,
        description = description,
        instructions = instructions,
        equipment = equipment,
    )
