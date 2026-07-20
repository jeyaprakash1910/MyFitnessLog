package com.myfitnesslog.feature.workout.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Pure JVM tests for the elapsed-time calculation. */
class WorkoutClockTest {

    private val start = Instant.ofEpochSecond(1_000)

    @Test
    fun elapsedIsNowMinusStartWhileRunning() {
        assertEquals(
            Duration.ofSeconds(65),
            WorkoutClock.elapsed(start, endedAt = null, now = start.plusSeconds(65)),
        )
    }

    @Test
    fun elapsedFreezesAtEndedAt() {
        // Even though "now" is far later, a finished workout uses endedAt.
        assertEquals(
            Duration.ofSeconds(120),
            WorkoutClock.elapsed(start, endedAt = start.plusSeconds(120), now = start.plusSeconds(600)),
        )
    }

    @Test
    fun elapsedIsNeverNegative() {
        assertEquals(
            Duration.ZERO,
            WorkoutClock.elapsed(start, endedAt = null, now = start.minusSeconds(10)),
        )
    }

    @Test
    fun reconstructsFromStartAloneAfterRecreation() {
        // No stored state: a fresh calculation from startedAt yields the same result.
        val now = start.plusSeconds(200)
        assertEquals(
            WorkoutClock.elapsed(start, null, now),
            WorkoutClock.elapsed(start, null, now),
        )
        assertEquals(Duration.ofSeconds(200), WorkoutClock.elapsed(start, null, now))
    }
}
