package com.myfitnesslog.service;

import com.myfitnesslog.entity.WorkoutSession;

/**
 * Result of an idempotent start: the persisted session plus whether it was newly
 * created ({@code true}) or an existing session converged in place
 * ({@code false}). The controller uses the flag to return 201 vs 200. Kept
 * HTTP-agnostic.
 */
public record WorkoutSessionSaveResult(WorkoutSession session, boolean created) {
}
