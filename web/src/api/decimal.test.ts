import { describe, expect, it } from 'vitest';
import { parseJsonPreservingDecimals } from './decimal';

/**
 * These tests are the guard on the one property most likely to be silently
 * broken: decimal scale. The `102.50` fixture is deliberate — it is the exact
 * value M9 traced end to end, and the value plain `JSON.parse` would corrupt.
 */
describe('parseJsonPreservingDecimals', () => {
  it('preserves the trailing-zero scale of weight as a string', () => {
    const parsed = parseJsonPreservingDecimals('{"weight":102.50}') as { weight: unknown };
    expect(parsed.weight).toBe('102.50');
    // The bug this prevents: JSON.parse collapses the scale.
    expect(JSON.parse('{"weight":102.50}').weight).toBe(102.5);
  });

  it('preserves rpe and rir scale', () => {
    const parsed = parseJsonPreservingDecimals('{"rpe":8.5,"rir":2.0}') as {
      rpe: unknown;
      rir: unknown;
    };
    expect(parsed.rpe).toBe('8.5');
    expect(parsed.rir).toBe('2.0');
  });

  it('leaves null decimal fields as null', () => {
    const parsed = parseJsonPreservingDecimals('{"weight":60.00,"rpe":null,"rir":null}') as {
      weight: unknown;
      rpe: unknown;
      rir: unknown;
    };
    expect(parsed.weight).toBe('60.00');
    expect(parsed.rpe).toBeNull();
    expect(parsed.rir).toBeNull();
  });

  it('keeps genuine integer fields as numbers', () => {
    const parsed = parseJsonPreservingDecimals(
      '{"setNumber":2,"repetitions":8,"exerciseOrder":0,"weight":80.00}',
    ) as Record<string, unknown>;
    expect(parsed.setNumber).toBe(2);
    expect(parsed.repetitions).toBe(8);
    expect(parsed.exerciseOrder).toBe(0);
    expect(typeof parsed.weight).toBe('string');
  });

  it('handles nested arrays of sets', () => {
    const raw = '{"sets":[{"weight":100.00,"rpe":9.0},{"weight":102.50,"rpe":null}]}';
    const parsed = parseJsonPreservingDecimals(raw) as {
      sets: Array<{ weight: unknown; rpe: unknown }>;
    };
    expect(parsed.sets[0]).toEqual({ weight: '100.00', rpe: '9.0' });
    expect(parsed.sets[1]).toEqual({ weight: '102.50', rpe: null });
  });

  it('does not corrupt a decimal key appearing inside free-text notes', () => {
    // The notes string mentions "weight": the JSON escaping (\") keeps the regex
    // from matching it, so the real weight is still the one that gets quoted.
    const raw = '{"notes":"felt heavy at \\"weight\\": 5","weight":80.00}';
    const parsed = parseJsonPreservingDecimals(raw) as { notes: string; weight: unknown };
    expect(parsed.weight).toBe('80.00');
    expect(parsed.notes).toBe('felt heavy at "weight": 5');
  });

  it('handles negative decimals', () => {
    const parsed = parseJsonPreservingDecimals('{"rir":-1.5}') as { rir: unknown };
    expect(parsed.rir).toBe('-1.5');
  });

  it('passes an empty body through untouched', () => {
    expect(parseJsonPreservingDecimals('')).toBe('');
  });
});
