package com.myfitnesslog.feature.routine.ui.picker

import java.util.UUID

/** Presentation model for a selectable exercise in the picker. */
data class ExercisePickerItem(
    val id: UUID,
    val name: String,
)

/** Immutable state for the exercise picker. */
data class ExercisePickerUiState(
    val query: String = "",
    val exercises: List<ExercisePickerItem> = emptyList(),
)
