package com.myfitnesslog.entity;

/**
 * Lifecycle state of a workout session (DATABASE.md). Persisted by name
 * (EnumType.STRING) to match the "WorkoutSession".status CHECK constraint and the
 * Android WorkoutStatus enum.
 */
public enum WorkoutStatus {
    IN_PROGRESS,
    COMPLETED,
    DISCARDED
}
