package com.myfitnesslog.feature.routine.ui.list

import java.util.UUID

/** Presentation model for a routine row. */
data class RoutineListItem(
    val id: UUID,
    val name: String,
)

/** Immutable state for the routine list (Home). Local-only, so no error state. */
sealed interface RoutineListUiState {
    data object Loading : RoutineListUiState
    data object Empty : RoutineListUiState
    data class Success(val routines: List<RoutineListItem>) : RoutineListUiState
}
