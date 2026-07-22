import type { DecimalString, IsoInstant, SetCategory, Uuid } from '@/api';

/**
 * Presentation formatting for workout history.
 *
 * This is a deliberate mirror of Android's `HistoryFormatting.kt`. The Android
 * app is the reference UX, and the same workout must not read differently on the
 * two clients — so each function here corresponds to one there, and the
 * cross-client fixtures in `history.test.ts` assert they produce identical
 * strings.
 *
 * There is no shared code between the platforms (MILESTONE_10_PLAN §1), so this
 * duplication is accepted and pinned down by tests rather than by a build step.
 *
 * All functions are pure and take the values they format, so a locale and time
 * zone can be injected in tests instead of depending on the machine's.
 */

/** Locale/zone overrides. Omitted in production so the viewer's own settings apply. */
export interface FormatOptions {
  locale?: string | undefined;
  timeZone?: string | undefined;
}

/**
 * Cache of `Intl.DateTimeFormat` instances, keyed by their configuration.
 *
 * Constructing a formatter is roughly **40× more expensive than using one**
 * (measured: 22.8µs vs 0.5µs per call). Building one per row per field is
 * invisible at today's scale — under 1 ms for a 21-workout list — but it grows
 * linearly with history: about 23 ms for a year of training, which exceeds a
 * 16 ms frame budget and would show up as jank on a modest device.
 *
 * The cache is unbounded by design: keys are the handful of (style, locale,
 * zone) combinations this app uses, not user input, so it cannot grow without
 * limit.
 */
const dateTimeFormatters = new Map<string, Intl.DateTimeFormat>();

function getFormatter(
  style: 'date' | 'time',
  { locale, timeZone }: FormatOptions,
): Intl.DateTimeFormat {
  const key = `${style}|${locale ?? ''}|${timeZone ?? ''}`;
  let formatter = dateTimeFormatters.get(key);
  if (formatter === undefined) {
    formatter = new Intl.DateTimeFormat(locale, {
      ...(style === 'date' ? { dateStyle: 'medium' as const } : { timeStyle: 'short' as const }),
      ...(timeZone ? { timeZone } : {}),
    });
    dateTimeFormatters.set(key, formatter);
  }
  return formatter;
}

/**
 * Medium localized date, e.g. `21 Jul 2026`.
 *
 * Mirrors Android's `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` —
 * both localize rather than hardcoding a pattern, so both follow the user's
 * regional format.
 */
export function formatWorkoutDate(startedAt: IsoInstant, options: FormatOptions = {}): string {
  return getFormatter('date', options).format(new Date(startedAt));
}

/** Short localized time of day, e.g. `10:15`. Used for the completion timestamp. */
export function formatTimeOfDay(instant: IsoInstant, options: FormatOptions = {}): string {
  return getFormatter('time', options).format(new Date(instant));
}

/**
 * Formats a duration as `Hh MMm`, or `Mm` under an hour — `1h 05m`, `45m`.
 *
 * Minutes are truncated, not rounded, matching Android's `Duration.toMinutes()`.
 */
export function formatWorkoutDuration(durationMs: number): string {
  const totalMinutes = Math.floor(Math.max(durationMs, 0) / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}h ${String(minutes).padStart(2, '0')}m` : `${minutes}m`;
}

/**
 * Duration of a completed workout, derived from its persisted timestamps.
 *
 * Duration is never stored on either client — it is always `endedAt − startedAt`
 * (ADR-0004 keeps history a snapshot of facts, not derived values). A negative
 * span clamps to zero, mirroring `WorkoutClock.elapsed`, so clock skew on the
 * recording device cannot render as a negative duration.
 */
export function formatCompletedDuration(startedAt: IsoInstant, endedAt: IsoInstant | null): string {
  const start = Date.parse(startedAt);
  const end = endedAt === null ? start : Date.parse(endedAt);
  return formatWorkoutDuration(end - start);
}

/** Distinguishes routine-based from manual workouts. Mirrors `workoutTypeLabel`. */
export function workoutTypeLabel(routineId: Uuid | null): string {
  return routineId === null ? 'Manual Workout' : 'Routine Workout';
}

/** Trims notes and drops blank values, so callers never render empty note text. */
export function sanitizeNotes(notes: string | null): string | null {
  if (notes === null) return null;
  const trimmed = notes.trim();
  return trimmed === '' ? null : trimmed;
}

/**
 * Drops trailing fractional zeros from a decimal string — `102.50` → `102.5`,
 * `80.00` → `80`.
 *
 * This is the string equivalent of Kotlin's
 * `BigDecimal.stripTrailingZeros().toPlainString()`, and it is done **textually
 * on purpose**. Routing the value through `Number` to trim it would reintroduce
 * exactly the IEEE-754 precision loss the whole `DecimalString` design exists to
 * prevent (see `api/decimal.ts`).
 *
 * Zeros are only stripped after a decimal point, so an integer like `100` keeps
 * its magnitude rather than becoming `1`.
 */
export function trimDecimalScale(value: DecimalString): string {
  if (!value.includes('.')) return value;
  return value.replace(/0+$/, '').replace(/\.$/, '');
}

/** True when a decimal string is zero in any written form (`0`, `0.00`, `-0.0`). */
function isZeroDecimal(value: DecimalString): boolean {
  return /^-?0*(\.0*)?$/.test(value);
}

/**
 * Formats `weight × reps`, e.g. `80 × 8` or `82.5 × 6`.
 *
 * Mirrors `formatWeightReps`: trailing zeros are trimmed for readability while
 * the value stays exact, and a zero (bodyweight) weight renders as `0` rather
 * than `0.0`. The separator is U+00D7 (×), matching Kotlin.
 */
export function formatWeightReps(weight: DecimalString, repetitions: number): string {
  const weightText = isZeroDecimal(weight) ? '0' : trimDecimalScale(weight);
  return `${weightText} × ${repetitions}`;
}

/** Human label for a set category, e.g. `WARMUP` → `Warm-up`. Mirrors `setCategoryLabel`. */
export function setCategoryLabel(category: SetCategory): string {
  switch (category) {
    case 'WARMUP':
      return 'Warm-up';
    case 'WORKING':
      return 'Working';
    case 'TOP_SET':
      return 'Top set';
    case 'BACKOFF':
      return 'Back-off';
  }
}

/** Formats an optional RPE as `RPE 8.5`, or null when absent. Mirrors `formatRpe`. */
export function formatRpe(rpe: DecimalString | null): string | null {
  return rpe === null ? null : `RPE ${trimDecimalScale(rpe)}`;
}

/**
 * Formats an optional RIR as `RIR 2`, or null when absent.
 *
 * Web-only: Android captures `rir` but does not render it on the detail screen.
 * Added because Phase 3 explicitly requires "RPE/RIR if present" and the data is
 * already in the payload. It follows `formatRpe`'s convention exactly so the two
 * read as a pair.
 */
export function formatRir(rir: DecimalString | null): string | null {
  return rir === null ? null : `RIR ${trimDecimalScale(rir)}`;
}
