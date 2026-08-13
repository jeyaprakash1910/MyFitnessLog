package com.myfitnesslog.feature.history.ui

import com.myfitnesslog.core.data.local.SetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID
import java.time.Duration
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
        // Zero elapsed reads as seconds, not "0m", which looked like the workout
        // never happened rather than that no time had passed.
        assertEquals("0s", formatCompletedDuration(start, null))
    }

    /**
     * Seconds appear only below a minute.
     *
     * Every test workout finishes in seconds and used to render "0m", which reads
     * as a bug. Above a minute seconds are noise, so they stop.
     */
    @Test
    fun durationShowsSecondsOnlyUnderAMinute() {
        assertEquals("38s", formatWorkoutDuration(Duration.ofSeconds(38)))
        assertEquals("59s", formatWorkoutDuration(Duration.ofSeconds(59)))
        assertEquals("1m", formatWorkoutDuration(Duration.ofSeconds(60)))
        assertEquals("42m", formatWorkoutDuration(Duration.ofSeconds(42 * 60 + 17)))
        assertEquals("1h 05m", formatWorkoutDuration(Duration.ofMinutes(65)))
    }

    /**
     * The routine's name is what a workout is called, when one was recorded.
     *
     * A manual workout has no routine, and a session from before the snapshot
     * existed has no name; both fall back rather than inventing one.
     */
    @Test
    fun workoutTitlePrefersTheRecordedRoutineName() {
        val routineId = UUID.randomUUID()
        assertEquals("Push", workoutTitle(routineId, "Push"))
        assertEquals("Manual Workout", workoutTitle(null, null))
        assertEquals("Routine Workout", workoutTitle(routineId, null))
        assertEquals("Routine Workout", workoutTitle(routineId, "   "))
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
