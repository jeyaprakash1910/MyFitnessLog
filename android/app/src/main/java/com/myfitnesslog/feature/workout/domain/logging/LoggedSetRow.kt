package com.myfitnesslog.feature.workout.domain.logging

import java.util.UUID

/**
 * One row in the active workout's set table (V2 Milestone A — pure domain).
 *
 * A row is either **transient** (no [persistedId] — a planned or partially-filled
 * intention that lives only in memory) or **persisted** (has a [persistedId] — a
 * completed set that exists as a `WorkoutSet` in Room). Persisting happens only on
 * the completion transition (INV-4/5); see [SetTransitions].
 *
 * @property setNumber 1-based position within the exercise.
 * @property input the currently-entered values.
 * @property persistedId the id of the backing `WorkoutSet` once completed, else null.
 */
data class LoggedSetRow(
    val setNumber: Int,
    val input: SetInput = SetInput.EMPTY,
    val persistedId: UUID? = null,
) {
    /** The row's resting logical state. */
    val state: SetRowState
        get() = when {
            persistedId != null -> SetRowState.COMPLETED
            input.isBlank -> SetRowState.PLANNED
            else -> SetRowState.PARTIALLY_FILLED
        }

    /** True once this row is backed by a persisted, completed set. */
    val isCompleted: Boolean get() = persistedId != null
}
