package com.myfitnesslog.service;

import com.myfitnesslog.entity.RoutineExercise;

/**
 * Result of an idempotent add: the persisted routine exercise plus whether it was
 * newly created ({@code true}) or an existing row updated in place
 * ({@code false}). The controller uses the flag to return 201 vs 200. Kept
 * HTTP-agnostic.
 */
public record RoutineExerciseSaveResult(RoutineExercise routineExercise, boolean created) {
}
