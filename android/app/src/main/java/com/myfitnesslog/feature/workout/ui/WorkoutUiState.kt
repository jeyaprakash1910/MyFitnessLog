package com.myfitnesslog.feature.workout.ui

import com.myfitnesslog.core.data.local.SetCategory
import java.math.BigDecimal
import java.util.UUID

/**
 * Presentation model for one row in the inline set table (V2 Milestone B).
 *
 * A row is either a **completed** set ([isCompleted] true, backed by a persisted
 * `WorkoutSet`) or a **planned/partial** transient row ([isCompleted] false). The
 * [rowKey] is stable across recompositions and is how the UI routes intents back to
 * the ViewModel. Raw fields ([rpe]/[rir]/[setCategory]) are carried — even though
 * Milestone B does not render RPE — so the ViewModel can reconstruct an edit without
 * re-reading Room.
 */
data class WorkoutSetRowUi(
    val rowKey: String,
    val setNumber: Int,
    /**
     * Stable ordering slot behind the (contiguous) [setNumber]. Completed rows carry
     * their persisted set number; planned rows carry their assigned order. Undo reuses
     * this so a reverted set lands back in its original position (see WorkoutRowMerger).
     */
    val slot: Int = 0,
    val weight: BigDecimal?,
    val repetitions: Int?,
    val rpe: BigDecimal?,
    val rir: BigDecimal?,
    val setCategory: SetCategory,
    val isCompleted: Boolean,
    /** Formatted previous-workout performance for this set number, or null → "-". */
    val previous: String? = null,
)

/** Presentation model for one snapshotted exercise and its rows. */
data class WorkoutExerciseUi(
    val id: UUID,
    val exerciseName: String,
    val targetSummary: String,
    /** Rest duration used when a set of this exercise is completed (spec §5). */
    val restSeconds: Int,
    val rows: List<WorkoutSetRowUi>,
    /** Free-text note for this exercise within the workout (null/blank when unset). */
    val notes: String? = null,
)

/** Immutable state for the active workout screen. */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    /** No workout is active and none was started (e.g. Workout tab with nothing running). */
    data object NoActiveWorkout : WorkoutUiState

    /**
     * A workout is loaded. [isReadOnly] is true once the session is no longer
     * IN_PROGRESS (completed/discarded) — the UI then hides mutating controls and
     * shows only completed rows.
     */
    data class Active(
        val sessionId: UUID,
        val exercises: List<WorkoutExerciseUi>,
        val isReadOnly: Boolean,
    ) : WorkoutUiState
}
