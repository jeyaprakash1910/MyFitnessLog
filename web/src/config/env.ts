/**
 * Typed, validated access to the environment configuration.
 *
 * Vite injects `import.meta.env` at build time. Reading it in exactly one place
 * means the rest of the app depends on a small typed value, not on the ambient
 * `import.meta.env` bag, and a missing or malformed value fails **here, at
 * startup**, with a clear message — rather than surfacing later as an Axios
 * request to `undefined/workout-sessions`.
 *
 * There is deliberately no hardcoded fallback URL: the Android client made the
 * backend address configurable in M9.5 (T3) precisely so it was not baked into
 * source, and the web client holds the same line.
 */

/**
 * Validates and normalizes a configured base URL.
 *
 * Exported separately from the module-level constant so it can be unit-tested
 * directly — importing the constant would evaluate `import.meta.env` and throw
 * before a test could assert anything about it.
 *
 * @throws Error when the value is absent or blank.
 */
export function resolveApiBaseUrl(value: string | undefined): string {
  if (value === undefined || value.trim() === '') {
    throw new Error(
      'Missing required environment variable VITE_API_BASE_URL. ' +
        'Copy .env.example to .env.local and set it (see web/README.md).',
    );
  }
  // Trailing slashes are stripped so joining a path never produces `//`.
  return value.trim().replace(/\/+$/, '');
}

/** The backend base URL, including the `/api/v1` prefix, without a trailing slash. */
export const API_BASE_URL: string = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL);
