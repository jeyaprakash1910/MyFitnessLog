package com.myfitnesslog.feature.routine.ui

import com.myfitnesslog.feature.routine.data.local.RoutineExerciseEntity

/**
 * Formats a routine exercise's targets into a short human-readable summary, e.g.
 * "3 sets · 8–12 reps · rest 90s". Presentation logic lives here (a UI concern),
 * never inside the entity.
 */
fun RoutineExerciseEntity.targetSummary(): String {
    val reps = if (minTargetReps == maxTargetReps) "$minTargetReps reps" else "$minTargetReps–$maxTargetReps reps"
    val parts = mutableListOf("$targetSets sets", reps)
    targetRestSeconds?.let { parts.add("rest ${it}s") }
    return parts.joinToString(" · ")
}
