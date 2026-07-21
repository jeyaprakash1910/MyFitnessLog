package com.myfitnesslog.entity;

/**
 * Role of a set within an exercise (DATABASE.md). Persisted by name
 * (EnumType.STRING) to match the "WorkoutSet".setCategory CHECK constraint and
 * the Android SetCategory enum.
 */
public enum SetCategory {
    WARMUP,
    WORKING,
    TOP_SET,
    BACKOFF
}
