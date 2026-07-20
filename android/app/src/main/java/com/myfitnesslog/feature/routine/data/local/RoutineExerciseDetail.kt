package com.myfitnesslog.feature.routine.data.local

import androidx.room.Embedded

/**
 * A routine exercise joined with its master exercise name, for display on the
 * Routine Detail / Edit screens. The name is read from the Exercise table so the
 * UI never has to look it up separately.
 */
data class RoutineExerciseDetail(
    @Embedded val routineExercise: RoutineExerciseEntity,
    val exerciseName: String,
)
