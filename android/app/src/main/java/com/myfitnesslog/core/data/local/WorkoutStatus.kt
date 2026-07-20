package com.myfitnesslog.core.data.local

/**
 * Lifecycle state of a workout session (DATABASE.md).
 *
 * A session starts IN_PROGRESS and becomes COMPLETED or DISCARDED exactly once.
 * Completed sessions are immutable history; discarded sessions are retained
 * (not deleted) and simply hidden from the user.
 */
enum class WorkoutStatus {
    IN_PROGRESS,
    COMPLETED,
    DISCARDED,
}
