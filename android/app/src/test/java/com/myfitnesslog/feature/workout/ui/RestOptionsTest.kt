package com.myfitnesslog.feature.workout.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests for the rest-timer picker's option ladder and label formatting. */
class RestOptionsTest {

    @Test
    fun optionsStartAtOffThenStepFiveToTwoMinutesThenFifteenToFive() {
        assertEquals(0, RestOptions.first()) // "Off"
        assertEquals(300, RestOptions.last()) // 5min

        // 5s steps up to 2min (120s).
        val fiveSecondBand = RestOptions.filter { it in 5..120 }
        assertEquals((5..120 step 5).toList(), fiveSecondBand)

        // 15s steps from 2min15s up to 5min.
        val fifteenSecondBand = RestOptions.filter { it > 120 }
        assertEquals((135..300 step 15).toList(), fifteenSecondBand)

        // Strictly increasing, no duplicates.
        assertEquals(RestOptions.sorted(), RestOptions)
        assertEquals(RestOptions.distinct(), RestOptions)
    }

    @Test
    fun formatsOffSecondsAndMinutes() {
        assertEquals("Off", formatRest(0))
        assertEquals("45s", formatRest(45))
        assertEquals("1min 0s", formatRest(60))
        assertEquals("1min 30s", formatRest(90))
        assertEquals("2min 0s", formatRest(120))
        assertEquals("5min 0s", formatRest(300))
    }
}
