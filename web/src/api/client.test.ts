import { describe, expect, it } from 'vitest';
import { apiClient } from './client';

/**
 * Verifies the client is wired the way the rest of the app assumes: configured
 * from the environment, and parsing responses without losing decimal scale.
 *
 * No network is involved — the response transform is invoked directly, which is
 * the unit that actually matters and the one a mocked HTTP layer would hide.
 */
describe('apiClient', () => {
  it('takes its base URL from the environment', () => {
    expect(apiClient.defaults.baseURL).toBe('http://test.local/api/v1');
  });

  const transform = () => {
    const t = apiClient.defaults.transformResponse;
    const fn = Array.isArray(t) ? t[0] : t;
    if (!fn) throw new Error('No response transform configured.');
    return fn as (data: unknown) => unknown;
  };

  it('parses a response body preserving decimal scale', () => {
    const parsed = transform()('{"weight":102.50,"repetitions":5}') as {
      weight: unknown;
      repetitions: unknown;
    };
    expect(parsed.weight).toBe('102.50');
    expect(parsed.repetitions).toBe(5);
  });

  it('passes a non-JSON body through instead of throwing inside the transform', () => {
    // A 204 with an empty body, or an HTML error page, must not blow up the
    // transform — the caller/interceptor decides what to do with it.
    expect(transform()('<html>oops</html>')).toBe('<html>oops</html>');
  });

  it('passes non-string data through untouched', () => {
    const already = { weight: '80.00' };
    expect(transform()(already)).toBe(already);
  });
});
