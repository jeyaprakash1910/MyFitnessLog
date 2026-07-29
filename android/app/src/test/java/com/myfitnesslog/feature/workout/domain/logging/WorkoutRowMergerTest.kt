package com.myfitnesslog.feature.workout.domain.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

/** Pure JVM tests for the slot-interleaved completed ∪ planned row merge + renumbering. */
class WorkoutRowMergerTest {

    // The [setNumber] carries the stable ordering slot the merge interleaves by.
    private fun completed(slot: Int) =
        LoggedSetRow(setNumber = slot, input = SetInput(BigDecimal("40"), 8), persistedId = UUID.randomUUID())

    private fun planned(slot: Int) = LoggedSetRow(setNumber = slot)

    @Test
    fun rowsAreInterleavedBySlot() {
        // Completed slots 1,3 and planned slots 2,4 → alternating completed/planned.
        val merged = WorkoutRowMerger.merge(listOf(completed(1), completed(3)), listOf(planned(2), planned(4)))
        assertEquals(4, merged.size)
        assertTrue(merged[0].isCompleted)
        assertEquals(SetRowState.PLANNED, merged[1].state)
        assertTrue(merged[2].isCompleted)
        assertEquals(SetRowState.PLANNED, merged[3].state)
    }

    @Test
    fun seededPlannedRowsRenderAfterCompletedWhenTheirSlotsAreHigher() {
        // Normal (non-undo) case: planned slots continue after completed ones.
        val merged = WorkoutRowMerger.merge(listOf(completed(1), completed(2)), listOf(planned(3), planned(4)))
        assertTrue(merged[0].isCompleted)
        assertTrue(merged[1].isCompleted)
        assertEquals(SetRowState.PLANNED, merged[2].state)
        assertEquals(SetRowState.PLANNED, merged[3].state)
    }

    @Test
    fun anUndoneMiddleSetStaysInPlace() {
        // Set 2 (of 1,2,3) is undone: it becomes a planned row reusing slot 2, so it
        // interleaves back between the remaining completed sets rather than jumping last.
        val merged = WorkoutRowMerger.merge(listOf(completed(1), completed(3)), listOf(planned(2)))
        assertEquals(listOf(1, 2, 3), merged.map { it.setNumber })
        assertTrue(merged[0].isCompleted)
        assertEquals(SetRowState.PLANNED, merged[1].state) // the reverted set, still in position 2
        assertTrue(merged[2].isCompleted)
    }

    @Test
    fun rowsAreRenumberedContiguouslyFromOne() {
        val merged = WorkoutRowMerger.merge(listOf(completed(3)), listOf(planned(4), planned(5), planned(6)))
        assertEquals(listOf(1, 2, 3, 4), merged.map { it.setNumber })
    }

    @Test
    fun emptyInputsYieldEmptyList() {
        assertTrue(WorkoutRowMerger.merge(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun onlyPlannedIsSupported() {
        val merged = WorkoutRowMerger.merge(emptyList(), listOf(planned(1), planned(2)))
        assertEquals(listOf(1, 2), merged.map { it.setNumber })
        assertTrue(merged.all { it.state == SetRowState.PLANNED })
    }
}
