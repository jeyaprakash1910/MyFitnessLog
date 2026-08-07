package com.myfitnesslog.feature.history.ui

import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.feature.workout.domain.WorkoutClock
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

/**
 * Presentation formatting for workout history. These are pure UI concerns kept
 * out of the entities (Entity Purity, ANDROID_ARCHITECTURE §9) and prepared in
 * the ViewModel so Compose never formats values itself.
 */

/**
 * Resolved per call rather than held in a `val`.
 *
 * A top-level `val` captures [Locale.getDefault] once, when the class is first
 * loaded. Android does not restart the process when the user changes their
 * language, so a cached formatter keeps rendering dates in the previous locale
 * until the app is killed.
 */
private fun dateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

/** Formats a workout's start instant as a medium local date, e.g. "21 Jul 2026". */
fun formatWorkoutDate(startedAt: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    dateFormatter().format(startedAt.atZone(zone))

/**
 * Formats the duration of a completed workout from its persisted timestamps.
 * Completed workouts always have `endedAt` set, so the elapsed value is fully
 * derived (never stored) — [WorkoutClock] freezes it at `endedAt`.
 */
fun formatCompletedDuration(startedAt: Instant, endedAt: Instant?): String =
    formatWorkoutDuration(WorkoutClock.elapsed(startedAt, endedAt, endedAt ?: startedAt))

/** Formats a workout duration as "Hh Mmm" (or "Mmm" under an hour), e.g. "1h 05m", "45m". */
fun formatWorkoutDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes.toString().padStart(2, '0')}m"
    } else {
        "${minutes}m"
    }
}

/** Human label distinguishing routine-based from manual (routineId == null) workouts. */
fun workoutTypeLabel(routineId: UUID?): String =
    if (routineId == null) "Manual Workout" else "Routine Workout"

/** Pluralised exercise-count label, e.g. "1 exercise", "3 exercises". */
fun exerciseCountLabel(count: Int): String =
    if (count == 1) "1 exercise" else "$count exercises"

/** Trims notes and drops blank values, so callers never render empty note text. */
fun sanitizeNotes(notes: String?): String? = notes?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Formats "weight × reps" for a set, e.g. "80 × 8" or "82.5 × 6". Trailing zeros
 * are trimmed for readability while keeping the value exact; a zero (bodyweight)
 * weight renders as "0" rather than BigDecimal's "0.0".
 */
fun formatWeightReps(weight: BigDecimal, reps: Int): String {
    val weightText = if (weight.signum() == 0) "0" else weight.stripTrailingZeros().toPlainString()
    return "$weightText × $reps"
}

/** Human label for a set category, e.g. WARMUP → "Warm-up". */
fun setCategoryLabel(category: SetCategory): String = when (category) {
    SetCategory.WARMUP -> "Warm-up"
    SetCategory.WORKING -> "Working"
    SetCategory.TOP_SET -> "Top set"
    SetCategory.BACKOFF -> "Back-off"
}

/** Formats an optional RPE as "RPE 8.5", or null when absent. */
fun formatRpe(rpe: BigDecimal?): String? =
    rpe?.let { "RPE ${it.stripTrailingZeros().toPlainString()}" }
