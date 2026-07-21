package com.myfitnesslog.service;

import com.myfitnesslog.entity.WorkoutExercise;

/**
 * Result of an idempotent add: the persisted workout exercise plus whether it was
 * newly created ({@code true}) or an existing row converged ({@code false}). The
 * controller uses the flag to return 201 vs 200. Kept HTTP-agnostic.
 */
public record WorkoutExerciseSaveResult(WorkoutExercise workoutExercise, boolean created) {
}
