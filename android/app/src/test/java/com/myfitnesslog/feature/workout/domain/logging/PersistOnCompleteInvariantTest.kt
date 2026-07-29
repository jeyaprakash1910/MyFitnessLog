package com.myfitnesslog.feature.workout.domain.logging

import com.myfitnesslog.core.data.local.SetCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

/**
 * Focused guard for the load-bearing invariant of Milestone A: **a persistence
 * mutation is produced ONLY by completing a transient row** (INV-4/5, DEC-1). Every
 * other transition on a transient (planned/partial) row must persist nothing.
 *
 * This is the test the "persist-on-complete" mutation testing targets: inverting the
 * `persistedId == null` guard in [SetTransitions.complete], or letting [edit]/[delete]
 * of a transient row emit an `Insert`/`Update`/`DeleteWithTombstone`, makes the
 * assertions below fail. (Mutation cycle performed manually during Milestone A: guard
 * inverted → these tests went red → guard restored → green.)
 */
class PersistOnCompleteInvariantTest {

    private val filled = SetInput(BigDecimal("40"), 8, BigDecimal("8"), SetCategory.WORKING)

    private fun persists(mutation: SetMutation): Boolean = when (mutation) {
        is SetMutation.Insert,
        is SetMutation.Update,
        is SetMutation.DeleteWithTombstone -> true
        SetMutation.RemoveTransientRow,
        SetMutation.None -> false
    }

    @Test
    fun theOnlyPersistingTransitionOnATransientRowIsCompletion() {
        val planned = LoggedSetRow(setNumber = 1)
        val partial = LoggedSetRow(setNumber = 1, input = SetInput(BigDecimal("40"), 8))

        // Editing / undoing / deleting a transient row persists nothing.
        assertFalse(persists(SetTransitions.edit(planned, filled).mutation))
        assertFalse(persists(SetTransitions.edit(partial, filled).mutation))
        assertFalse(persists(SetTransitions.undo(planned).mutation))
        assertFalse(persists(SetTransitions.undo(partial).mutation))
        assertFalse(persists(SetTransitions.delete(planned).mutation))
        assertFalse(persists(SetTransitions.delete(partial).mutation))

        // Only completing a filled transient row persists (an Insert).
        val completed = SetTransitions.complete(partial, filled)
        assertTrue(persists(completed.mutation))
        assertTrue(completed.mutation is SetMutation.Insert)
    }

    @Test
    fun completingAnEmptyTransientRowStillPersistsNothing() {
        val planned = LoggedSetRow(setNumber = 1)
        assertFalse(persists(SetTransitions.complete(planned, SetInput.EMPTY).mutation))
    }

    @Test
    fun aTransientRowNeverCarriesAPersistedId() {
        // No transition may fabricate a persisted id on a transient row; the id is
        // assigned by the caller only after repository.addSet succeeds.
        val planned = LoggedSetRow(setNumber = 1)
        assertFalse(SetTransitions.complete(planned, filled).row.persistedId != null)
        assertFalse(SetTransitions.edit(planned, filled).row.persistedId != null)
    }

    @Test
    fun undoNeverProducesAnUncompletedPersistedRow() {
        // DEC-2: undo tombstones + reverts; it must not leave a persisted row behind
        // (which would be the "isCompleted=false" anti-pattern we rejected).
        val result = SetTransitions.undo(LoggedSetRow(setNumber = 1, input = filled, persistedId = UUID.randomUUID()))
        assertTrue(result.mutation is SetMutation.DeleteWithTombstone)
        assertFalse(result.row.persistedId != null)
    }
}
