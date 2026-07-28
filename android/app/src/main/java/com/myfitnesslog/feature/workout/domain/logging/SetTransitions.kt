package com.myfitnesslog.feature.workout.domain.logging

import java.util.UUID

/**
 * The persistence action a transition asks the caller (ViewModel → repository) to
 * perform. Pure description — this layer never touches Room, sync, or the clock.
 */
sealed interface SetMutation {
    /** Persist a new performed set from [input] (the only path that creates a row). */
    data class Insert(val input: SetInput) : SetMutation

    /** Update the values of an already-persisted set (edit-after-completion). */
    data class Update(val id: UUID, val input: SetInput) : SetMutation

    /** Hard-delete a persisted set and record a tombstone (ADR-0007). */
    data class DeleteWithTombstone(val id: UUID) : SetMutation

    /** Remove a transient (never-persisted) row from the list; no persistence. */
    data object RemoveTransientRow : SetMutation

    /** Nothing to persist. */
    data object None : SetMutation
}

/** How a transition affects the single, session-scoped rest timer (spec §5, INV-7). */
enum class RestEffect { NONE, START, STOP }

/**
 * The outcome of a transition: what to persist ([mutation]), the row's new value
 * ([row]), and how the rest timer is affected ([rest]).
 *
 * For [SetMutation.Insert] the returned [row] is still transient (persistedId =
 * null); the caller assigns the persisted id after `repository.addSet(...)`
 * returns, because id allocation is a persistence concern, not a domain one.
 */
data class SetTransitionResult(
    val mutation: SetMutation,
    val row: LoggedSetRow,
    val rest: RestEffect,
)

/**
 * The set-row state machine (V2 Milestone A) — pure functions, fully unit-testable,
 * with no Android/Room/coroutine dependency. Encodes the frozen rules:
 *
 *  - **Completing is an event; editing is not** (INV-6). Only [complete] on a
 *    transient row persists anything and starts rest.
 *  - **Intent is transient** (INV-4/5): planned/partial rows never produce a
 *    persistence mutation except by being completed.
 *  - **Undo tombstones and reverts** (DEC-2, INV-13): never stores
 *    `isCompleted=false`.
 *  - **Duplicate-complete is idempotent**: completing an already-persisted row is a
 *    no-op (no second `WorkoutSet`).
 *
 * See docs/internal/V2/V2_MILESTONE_A_PREP.md §4 for the transition table.
 */
object SetTransitions {

    /**
     * Complete a set (the ✓ / RPE-sheet Done action).
     *  - transient + completable → persist ([SetMutation.Insert]) and start rest.
     *  - transient + not completable (empty weight/reps) → no-op (cannot complete
     *    an empty set).
     *  - already persisted → idempotent no-op (a duplicate tap creates no new row).
     */
    fun complete(row: LoggedSetRow, values: SetInput): SetTransitionResult = when {
        row.persistedId != null ->
            SetTransitionResult(SetMutation.None, row, RestEffect.NONE)
        !values.isCompletable ->
            SetTransitionResult(SetMutation.None, row.copy(input = values), RestEffect.NONE)
        else ->
            SetTransitionResult(SetMutation.Insert(values), row.copy(input = values), RestEffect.START)
    }

    /**
     * Edit a row's values.
     *  - persisted → [SetMutation.Update] (edit-after-completion): values only, the
     *    row stays COMPLETED, **no** rest effect.
     *  - transient → value change only, no persistence (PLANNED ↔ PARTIALLY_FILLED).
     */
    fun edit(row: LoggedSetRow, values: SetInput): SetTransitionResult =
        if (row.persistedId != null) {
            SetTransitionResult(SetMutation.Update(row.persistedId, values), row.copy(input = values), RestEffect.NONE)
        } else {
            SetTransitionResult(SetMutation.None, row.copy(input = values), RestEffect.NONE)
        }

    /**
     * Undo completion (uncheck ✓).
     *  - persisted → tombstone-delete and revert to a transient row (its typed
     *    values are kept); **stops** the rest timer.
     *  - transient → no-op (nothing to undo).
     */
    fun undo(row: LoggedSetRow): SetTransitionResult =
        if (row.persistedId != null) {
            SetTransitionResult(
                SetMutation.DeleteWithTombstone(row.persistedId),
                row.copy(persistedId = null),
                RestEffect.STOP,
            )
        } else {
            SetTransitionResult(SetMutation.None, row, RestEffect.NONE)
        }

    /**
     * Delete a row entirely.
     *  - persisted → tombstone-delete (rest unaffected — a rest already taken stands).
     *  - transient → remove from the list, no persistence, no sync.
     */
    fun delete(row: LoggedSetRow): SetTransitionResult =
        if (row.persistedId != null) {
            SetTransitionResult(SetMutation.DeleteWithTombstone(row.persistedId), row, RestEffect.NONE)
        } else {
            SetTransitionResult(SetMutation.RemoveTransientRow, row, RestEffect.NONE)
        }
}
