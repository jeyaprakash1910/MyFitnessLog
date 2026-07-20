package com.myfitnesslog.feature.history.ui.detail

import java.util.UUID

/** Presentation model for one performed set on the detail screen (read-only). */
data class WorkoutDetailSetRow(
    val id: UUID,
    val setNumber: Int,
    val weightReps: String,
    val category: String,
    val rpe: String?,
)

/** Presentation model for one snapshotted exercise and its sets (read-only). */
data class WorkoutDetailExerciseRow(
    val id: UUID,
    val position: Int,
    val name: String,
    val notes: String?,
    val sets: List<WorkoutDetailSetRow>,
)

/** Immutable state for the workout detail screen. */
sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState

    /** No completed workout exists for this id (e.g. discarded, in-progress, or deleted). */
    data object NotFound : WorkoutDetailUiState

    data class Success(
        val date: String,
        val duration: String,
        val typeLabel: String,
        val notes: String?,
        val exercises: List<WorkoutDetailExerciseRow>,
    ) : WorkoutDetailUiState
}
