package com.myfitnesslog.feature.routine.ui.picker

import java.util.UUID

/** Presentation model for a selectable exercise in the picker. */
data class ExercisePickerItem(
    val id: UUID,
    val name: String,
)

/** Presentation model for one selectable category chip. */
data class PickerCategoryItem(
    val id: UUID,
    val name: String,
)

/**
 * Immutable state for the exercise picker.
 *
 * [exercises] always comes from Room, so it is shown the instant the screen
 * opens regardless of connectivity (ADR-0002). [isRefreshing] and [errorMessage]
 * describe a background library download happening *alongside* that list — they
 * never replace it. A failed refresh with rows already cached is not worth
 * interrupting the user for, so the message is only surfaced when there is
 * nothing to show.
 */
data class ExercisePickerUiState(
    val query: String = "",
    val categories: List<PickerCategoryItem> = emptyList(),
    val selectedCategoryId: UUID? = null,
    val exercises: List<ExercisePickerItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    /** True when the list is empty because a download is still in flight. */
    val showLoading: Boolean get() = exercises.isEmpty() && isRefreshing

    /** True when the list is empty and the download failed — the only case worth reporting. */
    val showError: Boolean get() = exercises.isEmpty() && !isRefreshing && errorMessage != null

    /** True when the library genuinely has nothing matching, with no work outstanding. */
    val showEmpty: Boolean get() = exercises.isEmpty() && !isRefreshing && errorMessage == null
}
