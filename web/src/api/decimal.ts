import type { DecimalString } from './types';

/**
 * Decimal-preserving JSON parsing.
 *
 * ## The problem
 *
 * `weight`, `rpe` and `rir` are Postgres `NUMERIC` and arrive as JSON *numbers*
 * (`102.50`, `8.5`). `JSON.parse` turns every number into an IEEE-754 double, so
 * `102.50` becomes the value `102.5` and the trailing-zero scale is gone before
 * any reviver can see it. M9 preserved that scale end-to-end from the phone to
 * PostgreSQL; the web client must not discard it at the very last step.
 *
 * ## The approach
 *
 * A reviver cannot help — the precision is already lost by the time it runs. So
 * the raw response *text* is rewritten before parsing: the numeric value of each
 * known decimal key is wrapped in quotes, so `JSON.parse` yields the exact
 * original digits as a string. `"weight":102.50` → `"weight":"102.50"`.
 *
 * This is scoped to three known keys rather than "all numbers" on purpose:
 * `setNumber`, `repetitions`, `exerciseOrder` and the rest are genuine integers
 * and must stay numbers. Widening this later means adding a key here, one place.
 *
 * `null` values are left untouched (rpe/rir are optional), and the regex only
 * matches an unescaped `"key":number`, so a decimal key appearing *inside* a
 * free-text `notes` string is escaped (`\"weight\"`) and cannot false-match.
 */

/** Response keys whose numeric values must survive as exact decimal strings. */
const DECIMAL_KEYS = ['weight', 'rpe', 'rir'] as const;

const DECIMAL_KEY_PATTERN = new RegExp(
  `("(?:${DECIMAL_KEYS.join('|')})"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)`,
  'g',
);

/**
 * Parses JSON text, preserving the scale of known decimal fields as strings.
 *
 * Returns `unknown`: callers assert the concrete response shape, which the typed
 * API layer does at each endpoint.
 */
export function parseJsonPreservingDecimals(raw: string): unknown {
  if (raw === '' || raw == null) {
    return raw;
  }
  const quoted = raw.replace(DECIMAL_KEY_PATTERN, '$1"$2"');
  return JSON.parse(quoted);
}

/**
 * Narrows a parsed value to a {@link DecimalString}. The brand is a
 * compile-time-only marker; at runtime this is the identity on the string. Used
 * where a value is known to have come through {@link parseJsonPreservingDecimals}.
 */
export function asDecimalString(value: string): DecimalString {
  return value as DecimalString;
}
