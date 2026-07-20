package com.myfitnesslog.feature.routine.ui.detail

import java.util.UUID

/** Presentation model for one exercise row on the detail screen. */
data class RoutineExerciseRow(
    val id: UUID,
    val exerciseName: String,
    val targetSummary: String,
    val notes: String?,
)

/** Immutable state for the routine detail screen. */
sealed interface RoutineDetailUiState {
    data object Loading : RoutineDetailUiState

    /** The routine does not exist (or was deleted). */
    data object NotFound : RoutineDetailUiState

    data class Success(
        val name: String,
        val exercises: List<RoutineExerciseRow>,
    ) : RoutineDetailUiState
}
