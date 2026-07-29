package com.myfitnesslog.feature.workout.domain.logging

/**
 * Synthesises the transient planned rows shown when a workout starts (V2 spec §3).
 *
 * A routine exercise's `targetSets` becomes that many empty [LoggedSetRow]s — pure
 * UI/ViewModel state that is **never** persisted (INV-4). Milestone A provides only
 * the synthesis; merging with already-persisted sets and previous-workout prefill
 * arrive in later milestones (B/E).
 */
object PlannedRows {

    /** [targetSets] empty planned rows, numbered 1..n (n coerced to >= 0). */
    fun forTarget(targetSets: Int): List<LoggedSetRow> =
        (1..targetSets.coerceAtLeast(0)).map { number ->
            LoggedSetRow(setNumber = number, input = SetInput.EMPTY, persistedId = null)
        }
}
