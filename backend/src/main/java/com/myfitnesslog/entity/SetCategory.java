package com.myfitnesslog.entity;

/**
 * Role of a set within an exercise (DATABASE.md). Persisted by name
 * (EnumType.STRING) to match the workout_set.set_category CHECK constraint and
 * the Android SetCategory enum.
 */
public enum SetCategory {
    WARMUP,
    WORKING,
    TOP_SET,
    BACKOFF
}
