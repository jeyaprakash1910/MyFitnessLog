import { describe, expect, it } from 'vitest';
import type { DecimalString, SetCategory } from '@/api';
import {
  formatCompletedDuration,
  formatRir,
  formatRpe,
  formatTimeOfDay,
  formatWeightReps,
  formatWorkoutDate,
  formatWorkoutDuration,
  sanitizeNotes,
  setCategoryLabel,
  trimDecimalScale,
  workoutTypeLabel,
} from './history';

const dec = (value: string) => value as DecimalString;

// Pinned so the assertions do not depend on the machine's locale or zone.
const EN_GB = { locale: 'en-GB', timeZone: 'UTC' } as const;

/*
 * Cross-client parity.
 *
 * Every expected string in this file was captured by running the *real* Kotlin
 * in `HistoryFormatting.kt` over the same fixtures (2026-07-22, Locale.UK,
 * explicit zones) and comparing output. All 16 fixtures matched exactly,
 * including the Tokyo date rollover and the Europe/London DST boundary.
 *
 * These are therefore not guesses about what Android does — they are what it
 * actually produced. If a change here makes a test fail, the two clients have
 * diverged and the Kotlin is the reference.
 */

describe('formatWorkoutDuration', () => {
  it.each([
    [0, '0m'],
    [45 * 60_000, '45m'],
    [60 * 60_000, '1h 00m'],
    [65 * 60_000, '1h 05m'],
    [125 * 60_000, '2h 05m'],
    [90 * 60_000 + 59_000, '1h 30m'], // seconds truncate, never round up
  ])('formats %ims as %s', (ms, expected) => {
    expect(formatWorkoutDuration(ms)).toBe(expected);
  });

  it('clamps a negative duration to zero, matching WorkoutClock.elapsed', () => {
    expect(formatWorkoutDuration(-5000)).toBe('0m');
  });
});

describe('formatCompletedDuration', () => {
  it('derives the duration from the two timestamps', () => {
    expect(formatCompletedDuration('2026-07-21T09:00:00Z', '2026-07-21T10:05:00Z')).toBe('1h 05m');
  });

  it('yields zero when endedAt is absent rather than counting to now', () => {
    // Defensive: the history endpoint returns COMPLETED sessions, which always
    // have endedAt. If that ever changes, a stale row must not render a
    // duration that grows every render.
    expect(formatCompletedDuration('2026-07-21T09:00:00Z', null)).toBe('0m');
  });

  it('clamps a negative span from device clock skew', () => {
    expect(formatCompletedDuration('2026-07-21T10:00:00Z', '2026-07-21T09:00:00Z')).toBe('0m');
  });
});

describe('formatWorkoutDate', () => {
  it('formats a medium localized date', () => {
    expect(formatWorkoutDate('2026-07-21T09:00:00Z', EN_GB)).toBe('21 Jul 2026');
  });

  it('renders in the requested time zone, not UTC', () => {
    // 23:30 UTC is already the next day in Tokyo — the date shown must follow
    // the viewer's zone, as it does on the phone.
    expect(
      formatWorkoutDate('2026-07-21T23:30:00Z', { locale: 'en-GB', timeZone: 'Asia/Tokyo' }),
    ).toBe('22 Jul 2026');
  });

  it('survives a DST boundary', () => {
    // 2026-03-29 01:30 UTC is after the UK spring-forward transition (01:00 UTC).
    expect(
      formatWorkoutDate('2026-03-29T01:30:00Z', { locale: 'en-GB', timeZone: 'Europe/London' }),
    ).toBe('29 Mar 2026');
  });
});

describe('formatTimeOfDay', () => {
  it('formats a short localized time in the given zone', () => {
    expect(formatTimeOfDay('2026-07-21T10:15:00Z', EN_GB)).toBe('10:15');
  });

  it('shifts with the viewer time zone', () => {
    expect(
      formatTimeOfDay('2026-07-21T10:15:00Z', { locale: 'en-GB', timeZone: 'Asia/Kolkata' }),
    ).toBe('15:45');
  });
});

describe('workoutTypeLabel', () => {
  it('labels a null routineId as a manual workout', () => {
    expect(workoutTypeLabel(null)).toBe('Manual Workout');
  });

  it('labels a present routineId as a routine workout', () => {
    expect(workoutTypeLabel('3b1f7683-cb6c-483f-b5da-482f341e19a3')).toBe('Routine Workout');
  });
});

describe('sanitizeNotes', () => {
  it.each([
    [null, null],
    ['', null],
    ['   ', null],
    ['  felt strong  ', 'felt strong'],
  ])('maps %p to %p', (input, expected) => {
    expect(sanitizeNotes(input)).toBe(expected);
  });
});

/*
 * Detail-screen formatting parity.
 *
 * As above, every expectation here was captured from the real Kotlin
 * (`formatWeightReps`, `setCategoryLabel`, `formatRpe`) on 2026-07-22. All 17
 * fixtures matched.
 *
 * The two that matter most are `100` and `10.0`: a naive "strip trailing zeros"
 * turns 100 into 1, and a Number round-trip turns 102.50 into 102.5 by luck but
 * would eventually lose precision elsewhere.
 */

describe('trimDecimalScale', () => {
  it.each([
    ['80.00', '80'],
    ['102.50', '102.5'],
    ['60.000', '60'],
    ['10.0', '10'],
    ['7.25', '7.25'],
    ['82.5', '82.5'],
    ['100', '100'], // no decimal point: magnitude must survive
    ['0', '0'],
  ])('trims %s to %s', (input, expected) => {
    expect(trimDecimalScale(dec(input))).toBe(expected);
  });

  it('never routes the value through a JS number', () => {
    // A scale far beyond IEEE-754 exact integers survives untouched.
    expect(trimDecimalScale(dec('9007199254740993.10'))).toBe('9007199254740993.1');
  });
});

describe('formatWeightReps', () => {
  it.each([
    ['80.00', '80 × 8'],
    ['102.50', '102.5 × 8'],
    ['82.5', '82.5 × 8'],
    ['0.00', '0 × 8'],
    ['0', '0 × 8'],
    ['100', '100 × 8'],
    ['60.000', '60 × 8'],
    ['7.25', '7.25 × 8'],
    ['10.0', '10 × 8'],
  ])('formats weight %s as %s', (weight, expected) => {
    expect(formatWeightReps(dec(weight), 8)).toBe(expected);
  });

  it('uses the multiplication sign, not the letter x', () => {
    expect(formatWeightReps(dec('80.00'), 8)).toContain('×');
  });
});

describe('setCategoryLabel', () => {
  it.each([
    ['WARMUP', 'Warm-up'],
    ['WORKING', 'Working'],
    ['TOP_SET', 'Top set'],
    ['BACKOFF', 'Back-off'],
  ])('labels %s as %s', (category, expected) => {
    expect(setCategoryLabel(category as SetCategory)).toBe(expected);
  });
});

describe('formatRpe', () => {
  it.each([
    ['8.5', 'RPE 8.5'],
    ['9.0', 'RPE 9'],
    ['10', 'RPE 10'],
    ['7.25', 'RPE 7.25'],
  ])('formats %s as %s', (rpe, expected) => {
    expect(formatRpe(dec(rpe))).toBe(expected);
  });

  it('returns null when absent', () => {
    expect(formatRpe(null)).toBeNull();
  });
});

describe('formatRir', () => {
  // Web-only (Android captures rir but does not render it); follows formatRpe.
  it.each([
    ['2', 'RIR 2'],
    ['1.0', 'RIR 1'],
    ['0.5', 'RIR 0.5'],
  ])('formats %s as %s', (rir, expected) => {
    expect(formatRir(dec(rir))).toBe(expected);
  });

  it('returns null when absent', () => {
    expect(formatRir(null)).toBeNull();
  });
});
