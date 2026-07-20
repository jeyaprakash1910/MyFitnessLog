package com.myfitnesslog.feature.workout.domain

import java.time.Duration
import java.time.Instant

/**
 * Pure elapsed-time calculation for a workout. Kept as a stateless function so
 * it is trivially unit-testable and can be recomputed from persisted data alone.
 *
 * The elapsed duration is never stored: it is always derived from the session's
 * `startedAt` and the current time. Once the workout ends, `endedAt` freezes the
 * value (so the timer stops automatically).
 */
object WorkoutClock {

    fun elapsed(startedAt: Instant, endedAt: Instant?, now: Instant): Duration {
        val end = endedAt ?: now
        val duration = Duration.between(startedAt, end)
        return if (duration.isNegative) Duration.ZERO else duration
    }
}
