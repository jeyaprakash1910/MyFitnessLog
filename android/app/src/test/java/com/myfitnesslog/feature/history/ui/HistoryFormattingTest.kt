package com.myfitnesslog.feature.history.ui

import com.myfitnesslog.core.data.local.SetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Pure unit tests for the history presentation formatters. No Robolectric needed —
 * these are deterministic string/number transformations.
 */
class HistoryFormattingTest {

    private val start = Instant.ofEpochSecond(1_000_000)

    @Test
    fun formatCompletedDurationDerivesFromTimestamps() {
        assertEquals("1h 05m", formatCompletedDuration(start, start.plusSeconds(3_900)))
        assertEquals("45m", formatCompletedDuration(start, start.plusSeconds(2_700)))
    }

    @Test
    fun formatCompletedDurationIsZeroWhenNotEnded() {
        assertEquals("0m", formatCompletedDuration(start, null))
    }

    @Test
    fun formatWeightRepsTrimsTrailingZeros() {
        assertEquals("80 × 8", formatWeightReps(BigDecimal("80.00"), 8))
        assertEquals("82.5 × 6", formatWeightReps(BigDecimal("82.50"), 6))
    }

    @Test
    fun formatWeightRepsRendersZeroWeightAsZero() {
        assertEquals("0 × 12", formatWeightReps(BigDecimal("0.00"), 12))
    }

    @Test
    fun sanitizeNotesTrimsAndDropsBlanks() {
        assertEquals("Felt strong", sanitizeNotes("  Felt strong  "))
        assertNull(sanitizeNotes("   "))
        assertNull(sanitizeNotes(null))
    }

    @Test
    fun setCategoryLabelIsHumanReadable() {
        assertEquals("Warm-up", setCategoryLabel(SetCategory.WARMUP))
        assertEquals("Top set", setCategoryLabel(SetCategory.TOP_SET))
        assertEquals("Back-off", setCategoryLabel(SetCategory.BACKOFF))
    }

    @Test
    fun formatRpeIsNullWhenAbsent() {
        assertEquals("RPE 8.5", formatRpe(BigDecimal("8.5")))
        assertEquals("RPE 8", formatRpe(BigDecimal("8.0")))
        assertNull(formatRpe(null))
    }

    @Test
    fun workoutTypeLabelDistinguishesManual() {
        assertEquals("Manual Workout", workoutTypeLabel(null))
        assertEquals("Routine Workout", workoutTypeLabel(java.util.UUID.randomUUID()))
    }
}
