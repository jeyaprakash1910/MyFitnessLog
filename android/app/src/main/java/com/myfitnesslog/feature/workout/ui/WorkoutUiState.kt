package com.myfitnesslog.feature.workout.ui

import com.myfitnesslog.core.data.local.SetCategory
import java.math.BigDecimal
import java.util.UUID

/** Presentation model for one performed set. Carries raw fields so the ViewModel
 *  can reconstruct an update (e.g. toggling completion). */
data class WorkoutSetUi(
    val id: UUID,
    val setNumber: Int,
    val weight: BigDecimal,
    val repetitions: Int,
    val setCategory: SetCategory,
    val rpe: BigDecimal?,
    val rir: BigDecimal?,
    val isCompleted: Boolean,
)

/** Presentation model for one snapshotted exercise and its sets. */
data class WorkoutExerciseUi(
    val id: UUID,
    val exerciseName: String,
    val targetSummary: String,
    val sets: List<WorkoutSetUi>,
)

/** Immutable state for the active workout screen. */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    /** No workout is active and none was started (e.g. Workout tab with nothing running). */
    data object NoActiveWorkout : WorkoutUiState

    /**
     * A workout is loaded. [isReadOnly] is true once the session is no longer
     * IN_PROGRESS (completed/discarded) — the UI then hides mutating controls.
     */
    data class Active(
        val sessionId: UUID,
        val exercises: List<WorkoutExerciseUi>,
        val isReadOnly: Boolean,
    ) : WorkoutUiState
}
