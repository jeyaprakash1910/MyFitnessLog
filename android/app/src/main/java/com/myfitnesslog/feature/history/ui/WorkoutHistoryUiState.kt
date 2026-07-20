package com.myfitnesslog.feature.history.ui

import java.util.UUID

/**
 * Presentation model for one completed-workout row. All display values are
 * pre-formatted in the ViewModel so the stateless content renders strings
 * directly and performs no derivation.
 */
data class WorkoutHistoryItem(
    val id: UUID,
    val date: String,
    val duration: String,
    val typeLabel: String,
    val exercisesLabel: String,
    val notesPreview: String?,
)

/** Immutable state for the workout history list. Local-only, so no error state. */
sealed interface WorkoutHistoryUiState {
    data object Loading : WorkoutHistoryUiState
    data object Empty : WorkoutHistoryUiState
    data class Success(val workouts: List<WorkoutHistoryItem>) : WorkoutHistoryUiState
}
