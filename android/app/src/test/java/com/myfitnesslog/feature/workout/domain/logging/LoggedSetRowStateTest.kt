package com.myfitnesslog.feature.workout.domain.logging

import com.myfitnesslog.core.data.local.SetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

/** Pure JVM tests for the resting-state derivation of a set row. */
class LoggedSetRowStateTest {

    private fun input(weight: String? = null, reps: Int? = null, rpe: String? = null) =
        SetInput(
            weight = weight?.let(::BigDecimal),
            repetitions = reps,
            rpe = rpe?.let(::BigDecimal),
            setCategory = SetCategory.WORKING,
        )

    @Test
    fun emptyTransientRowIsPlanned() {
        val row = LoggedSetRow(setNumber = 1)
        assertEquals(SetRowState.PLANNED, row.state)
        assertFalse(row.isCompleted)
    }

    @Test
    fun transientRowWithValuesIsPartiallyFilled() {
        val row = LoggedSetRow(setNumber = 1, input = input(weight = "40", reps = 8))
        assertEquals(SetRowState.PARTIALLY_FILLED, row.state)
        assertFalse(row.isCompleted)
    }

    @Test
    fun rpeAloneStillCountsAsPartiallyFilled() {
        val row = LoggedSetRow(setNumber = 1, input = input(rpe = "8"))
        assertEquals(SetRowState.PARTIALLY_FILLED, row.state)
    }

    @Test
    fun persistedRowIsCompleted() {
        val row = LoggedSetRow(setNumber = 1, input = input(weight = "40", reps = 8), persistedId = UUID.randomUUID())
        assertEquals(SetRowState.COMPLETED, row.state)
        assertTrue(row.isCompleted)
    }

    @Test
    fun completableRequiresWeightAndReps() {
        assertFalse(input().isCompletable)
        assertFalse(input(weight = "40").isCompletable)
        assertFalse(input(reps = 8).isCompletable)
        assertTrue(input(weight = "40", reps = 8).isCompletable)
        // Bodyweight: zero weight is still completable.
        assertTrue(input(weight = "0", reps = 12).isCompletable)
    }
}
