package com.myfitnesslog.feature.workout.domain.logging

/**
 * Merges an exercise's **persisted completed** rows with its **transient planned**
 * rows into the ordered list the UI renders (V2 Milestone B — pure domain).
 *
 * Every row carries a stable **slot** in its [LoggedSetRow.setNumber]: a completed
 * row's slot is its persisted set number, a planned row's slot is its assigned order.
 * Rows are interleaved by slot (a *stable* sort, so equal slots keep input order),
 * then renumbered 1..n so displayed set numbers stay contiguous after adds, deletes,
 * and undos. Slot-ordering (rather than "completed always first") is what lets an
 * undone set fall back into its original position instead of jumping to the end: the
 * restored planned row simply reuses the vacated set's slot. This is the only merge
 * logic and it is intentionally pure so it is unit-testable and the ViewModel stays
 * orchestration-only.
 */
object WorkoutRowMerger {

    fun merge(completed: List<LoggedSetRow>, planned: List<LoggedSetRow>): List<LoggedSetRow> =
        (completed + planned)
            .sortedBy { it.setNumber }
            .mapIndexed { index, row -> row.copy(setNumber = index + 1) }
}
