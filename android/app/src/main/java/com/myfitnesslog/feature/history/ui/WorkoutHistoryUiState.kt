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
    /**
     * What the workout is called: the routine's name as recorded at start, or
     * "Manual Workout" when there was no routine. The headline of the card,
     * because "Push" says more at a glance than a date does.
     */
    val title: String,
    val exercisesLabel: String,
    val notesPreview: String?,
)

/** Immutable state for the workout history list. Local-only, so no error state. */
sealed interface WorkoutHistoryUiState {
    data object Loading : WorkoutHistoryUiState
    data object Empty : WorkoutHistoryUiState
    data class Success(val workouts: List<WorkoutHistoryItem>) : WorkoutHistoryUiState
}
