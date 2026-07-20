package com.myfitnesslog.feature.routine.ui.edit

import java.util.UUID

/** Presentation model for an editable exercise row (carries raw targets too). */
data class RoutineExerciseEditRow(
    val id: UUID,
    val exerciseName: String,
    val targetSummary: String,
    val targetSets: Int,
    val minTargetReps: Int,
    val maxTargetReps: Int,
    val targetRestSeconds: Int?,
    val notes: String?,
)

/** Immutable state for the routine edit screen. */
sealed interface RoutineEditUiState {
    data object Loading : RoutineEditUiState

    data class Success(
        val name: String,
        val exercises: List<RoutineExerciseEditRow>,
    ) : RoutineEditUiState
}
