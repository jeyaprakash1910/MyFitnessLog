import { describe, expect, it } from 'vitest';
import { resolveApiBaseUrl } from './env';

describe('resolveApiBaseUrl', () => {
  it('accepts a configured URL unchanged', () => {
    expect(resolveApiBaseUrl('http://localhost:8080/api/v1')).toBe('http://localhost:8080/api/v1');
  });

  it('strips trailing slashes so joined paths never double up', () => {
    expect(resolveApiBaseUrl('http://localhost:8080/api/v1//')).toBe(
      'http://localhost:8080/api/v1',
    );
  });

  it('trims surrounding whitespace', () => {
    expect(resolveApiBaseUrl('  http://host/api/v1  ')).toBe('http://host/api/v1');
  });

  it.each([undefined, '', '   '])('fails fast for a missing or blank value (%p)', (value) => {
    // Failing at startup is the point: the alternative is requests to
    // "undefined/workout-sessions" surfacing much later as a confusing 404.
    expect(() => resolveApiBaseUrl(value)).toThrow(/VITE_API_BASE_URL/);
  });
});
