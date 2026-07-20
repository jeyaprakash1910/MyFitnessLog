package com.myfitnesslog.feature.exercise.ui

import java.util.UUID

/**
 * Presentation model for a single row in the exercise list.
 *
 * The ViewModel maps [com.myfitnesslog.feature.exercise.data.local.ExerciseEntity]
 * to this so Room entities never reach the Compose layer (entity-purity rule,
 * docs/ANDROID_ARCHITECTURE.md). It carries only what the row renders.
 */
data class ExerciseListItem(
    val id: UUID,
    val name: String,
    val equipment: String?,
)

/**
 * Presentation model for a selectable category chip.
 */
data class CategoryFilterItem(
    val id: UUID,
    val name: String,
)

/**
 * Whole-screen immutable state for the exercise library: the filter controls
 * (search text, available categories, current selection) plus the list-area
 * status ([listState]). Kept as one object so the screen renders from a single
 * source of truth. The filter controls stay visible regardless of [listState].
 */
data class ExerciseLibraryUiState(
    val query: String = "",
    val categories: List<CategoryFilterItem> = emptyList(),
    val selectedCategoryId: UUID? = null,
    val listState: ExerciseListUiState = ExerciseListUiState.Loading,
)

/**
 * Status of the list area. The ViewModel owns every transition between these
 * states; the UI simply renders the current one.
 */
sealed interface ExerciseListUiState {

    /** Initial load in progress and no cached data to show yet. */
    data object Loading : ExerciseListUiState

    /** At least one exercise is available (from Room, the source of truth). */
    data class Success(val exercises: List<ExerciseListItem>) : ExerciseListUiState

    /** Refresh finished with no data and nothing cached. */
    data object Empty : ExerciseListUiState

    /** Refresh failed and there is no cached data to fall back to. */
    data class Error(val message: String) : ExerciseListUiState
}
