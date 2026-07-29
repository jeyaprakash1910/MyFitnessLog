package com.myfitnesslog.feature.workout.domain.logging

import com.myfitnesslog.core.data.local.SetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

/**
 * Pure JVM tests for the set-row state machine (V2 Milestone A). These encode the
 * transition table in docs/internal/V2/V2_MILESTONE_A_PREP.md §4 and the invariants
 * INV-4/5/6/13.
 */
class SetTransitionsTest {

    private val filled = SetInput(BigDecimal("40"), 8, BigDecimal("8"), SetCategory.WORKING)
    private val empty = SetInput.EMPTY

    private fun planned(number: Int = 1) = LoggedSetRow(setNumber = number)
    private fun persisted(id: UUID = UUID.randomUUID()) =
        LoggedSetRow(setNumber = 1, input = filled, persistedId = id)

    // --- complete -----------------------------------------------------------

    @Test
    fun completingATransientFilledRowInsertsAndStartsRest() {
        val result = SetTransitions.complete(planned(), filled)
        assertEquals(SetMutation.Insert(filled), result.mutation)
        assertEquals(RestEffect.START, result.rest)
        // The returned row is still transient; the caller assigns the id post-persist.
        assertNull(result.row.persistedId)
    }

    @Test
    fun completingAnEmptyRowIsANoOp() {
        val result = SetTransitions.complete(planned(), empty)
        assertEquals(SetMutation.None, result.mutation)
        assertEquals(RestEffect.NONE, result.rest)
    }

    @Test
    fun completingAnAlreadyCompletedRowIsIdempotent() {
        val row = persisted()
        val result = SetTransitions.complete(row, filled)
        // No second WorkoutSet is ever created by a duplicate complete tap.
        assertEquals(SetMutation.None, result.mutation)
        assertEquals(RestEffect.NONE, result.rest)
        assertSame(row, result.row)
    }

    // --- edit ---------------------------------------------------------------

    @Test
    fun editingACompletedRowUpdatesValuesOnlyWithNoRestEffect() {
        val id = UUID.randomUUID()
        val newValues = filled.copy(repetitions = 10)
        val result = SetTransitions.edit(persisted(id), newValues)
        assertEquals(SetMutation.Update(id, newValues), result.mutation)
        assertEquals(RestEffect.NONE, result.rest)
        // Stays completed (green): persisted id preserved.
        assertEquals(id, result.row.persistedId)
    }

    @Test
    fun editingATransientRowDoesNotPersist() {
        val newValues = SetInput(BigDecimal("20"), 5)
        val result = SetTransitions.edit(planned(), newValues)
        assertEquals(SetMutation.None, result.mutation)
        assertEquals(newValues, result.row.input)
        assertNull(result.row.persistedId)
    }

    // --- undo ---------------------------------------------------------------

    @Test
    fun undoingACompletedRowTombstonesAndRevertsToTransientAndStopsRest() {
        val id = UUID.randomUUID()
        val result = SetTransitions.undo(persisted(id))
        assertEquals(SetMutation.DeleteWithTombstone(id), result.mutation)
        assertEquals(RestEffect.STOP, result.rest)
        // Reverts to a transient row (never stores isCompleted=false).
        assertNull(result.row.persistedId)
        // Typed values are retained so the user can re-complete.
        assertEquals(filled, result.row.input)
    }

    @Test
    fun undoingATransientRowIsANoOp() {
        val result = SetTransitions.undo(planned())
        assertEquals(SetMutation.None, result.mutation)
        assertEquals(RestEffect.NONE, result.rest)
    }

    // --- delete -------------------------------------------------------------

    @Test
    fun deletingACompletedRowTombstonesAndDoesNotAffectRest() {
        val id = UUID.randomUUID()
        val result = SetTransitions.delete(persisted(id))
        assertEquals(SetMutation.DeleteWithTombstone(id), result.mutation)
        assertEquals(RestEffect.NONE, result.rest)
    }

    @Test
    fun deletingATransientRowRemovesItWithoutPersistence() {
        val result = SetTransitions.delete(planned())
        assertEquals(SetMutation.RemoveTransientRow, result.mutation)
        assertEquals(RestEffect.NONE, result.rest)
    }

    // --- planned-row synthesis ---------------------------------------------

    @Test
    fun plannedRowsAreSynthesisedFromTargetSets() {
        val rows = PlannedRows.forTarget(4)
        assertEquals(4, rows.size)
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.setNumber })
        assertTrue(rows.all { it.state == SetRowState.PLANNED })
        assertTrue(rows.all { it.persistedId == null })
    }

    @Test
    fun nonPositiveTargetYieldsNoPlannedRows() {
        assertTrue(PlannedRows.forTarget(0).isEmpty())
        assertTrue(PlannedRows.forTarget(-3).isEmpty())
    }
}
