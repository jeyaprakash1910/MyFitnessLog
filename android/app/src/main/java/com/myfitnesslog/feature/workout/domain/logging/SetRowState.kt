package com.myfitnesslog.feature.workout.domain.logging

/**
 * The **resting** logical states a set row can hold (V2 spec §3).
 *
 * The specification names five states: PLANNED, PARTIALLY_FILLED, COMPLETED,
 * EDITED_AFTER_COMPLETION, and UNCOMPLETED. Only three of those are states a row
 * *rests* in; the other two are **transitions**, distinguished only by whether
 * side effects fire (spec §3), and are therefore modelled by [SetTransitions]
 * rather than as stored states:
 *
 *  - **EDITED_AFTER_COMPLETION** = editing a [COMPLETED] row. The row stays
 *    COMPLETED; only its values change, and no side effects fire
 *    (`SetMutation.Update`, `RestEffect.NONE`).
 *  - **UNCOMPLETED** = the transient result of undoing a [COMPLETED] row. The row
 *    loses its persisted id and returns to [PLANNED]/[PARTIALLY_FILLED]
 *    (`SetMutation.DeleteWithTombstone`, `RestEffect.STOP`).
 *
 * Keeping resting state to these three values is what makes "completing is an
 * event, editing is not" (INV-6) precise: a row is either intent (not persisted)
 * or a performed fact (persisted).
 */
enum class SetRowState {
    /** Transient, no values entered — a placeholder from the routine's target set count. */
    PLANNED,

    /** Transient, some values entered but not yet completed. */
    PARTIALLY_FILLED,

    /** Persisted as a performed `WorkoutSet` (green in the UI). */
    COMPLETED,
}
