package com.myfitnesslog.feature.workout.domain

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Virtual-time tests for the rest countdown, independent of Compose. */
class RestTimerTest {

    @Test
    fun countsDownEachSecondToFinished() = runTest {
        val timer = RestTimer(backgroundScope, tickMillis = 1_000)

        timer.start(3)
        assertEquals(RestTimerState.Running(3, 3), timer.state.value)

        advanceTimeBy(1_000); runCurrent()
        assertEquals(RestTimerState.Running(2, 3), timer.state.value)

        advanceTimeBy(1_000); runCurrent()
        assertEquals(RestTimerState.Running(1, 3), timer.state.value)

        advanceTimeBy(1_000); runCurrent()
        assertEquals(RestTimerState.Finished, timer.state.value)
    }

    @Test
    fun cancelReturnsToIdle() = runTest {
        val timer = RestTimer(backgroundScope)
        timer.start(5)
        timer.cancel()
        assertEquals(RestTimerState.Idle, timer.state.value)
    }

    @Test
    fun skipGoesStraightToFinished() = runTest {
        val timer = RestTimer(backgroundScope)
        timer.start(5)
        timer.skip()
        assertEquals(RestTimerState.Finished, timer.state.value)
    }

    @Test
    fun restartResetsToFullDuration() = runTest {
        val timer = RestTimer(backgroundScope, tickMillis = 1_000)
        timer.start(3)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(RestTimerState.Running(2, 3), timer.state.value)

        timer.restart()
        assertEquals(RestTimerState.Running(3, 3), timer.state.value)
    }

    @Test
    fun startWithNonPositiveDurationIsIgnored() = runTest {
        val timer = RestTimer(backgroundScope)
        timer.start(0)
        assertEquals(RestTimerState.Idle, timer.state.value)
    }
}
