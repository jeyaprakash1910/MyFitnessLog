package com.myfitnesslog.service;

import com.myfitnesslog.entity.Routine;

/**
 * Result of an idempotent routine save: the persisted routine plus whether it was
 * newly created ({@code true}) or an existing routine updated in place
 * ({@code false}). The controller uses the flag to return 201 Created vs 200 OK.
 * Kept HTTP-agnostic — the service exposes the fact, not the status code.
 */
public record RoutineSaveResult(Routine routine, boolean created) {
}
