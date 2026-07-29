package com.myfitnesslog.feature.workout.domain.logging

import com.myfitnesslog.core.data.local.SetCategory
import java.math.BigDecimal

/**
 * The editable values of a single set row while a workout is being logged
 * (V2 Milestone A — pure domain, no Android/Room dependency).
 *
 * These are *intent* until the row is completed: an empty or partially-typed row
 * is never persisted (System Invariant INV-4). Value validation (weight >= 0,
 * rpe in 1..10, etc.) remains the repository's responsibility; this type only
 * models presence/absence of the fields the UI edits.
 *
 * @see SetTransitions for the state machine that consumes these values.
 */
data class SetInput(
    val weight: BigDecimal? = null,
    val repetitions: Int? = null,
    val rpe: BigDecimal? = null,
    val setCategory: SetCategory = SetCategory.WORKING,
) {
    /** No measured value has been entered yet (RPE alone does not "fill" a set). */
    val isBlank: Boolean get() = weight == null && repetitions == null && rpe == null

    /**
     * Whether this row carries enough to become a performed set. A set needs a
     * weight (0 is valid for bodyweight) and a rep count; RPE is optional.
     */
    val isCompletable: Boolean get() = weight != null && repetitions != null

    companion object {
        /** An empty planned row's input. */
        val EMPTY = SetInput()
    }
}
