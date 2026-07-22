import axios, { type AxiosInstance } from 'axios';
import { API_BASE_URL } from '@/config/env';
import { parseJsonPreservingDecimals } from './decimal';
import { normalizeApiError } from './errors';

/**
 * The shared Axios instance every request goes through.
 *
 * Three cross-cutting concerns are centralized here so no endpoint re-implements
 * them:
 *
 * 1. **Base URL** from the validated environment (`src/config/env.ts`) — never
 *    hardcoded, matching the Android client's configurable backend address.
 * 2. **Decimal-safe parsing.** The default `transformResponse` is replaced so
 *    response bodies are parsed with {@link parseJsonPreservingDecimals}, keeping
 *    `weight`/`rpe`/`rir` scale intact. This must live at the client level: once
 *    Axios has JSON-parsed the body the precision is already gone.
 * 3. **Error normalization.** A response interceptor converts every failure into
 *    an {@link ApiError} via {@link normalizeApiError}, so callers branch on a
 *    small stable shape instead of raw `AxiosError`.
 *
 * The instance is read-only in use (V1 web client is read-only, SYNC.md §6); no
 * request interceptor adds auth because V1 has none.
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: {
    Accept: 'application/json',
  },
  // Parse JSON ourselves to preserve decimal scale. Axios hands us the raw text
  // when we override transformResponse; a non-string (already-parsed, or a
  // network error with no body) is passed through untouched.
  transformResponse: [
    (data: unknown) => {
      if (typeof data !== 'string') {
        return data;
      }
      try {
        return parseJsonPreservingDecimals(data);
      } catch {
        // A non-JSON body (e.g. an empty 204, or an HTML error page). Let the
        // caller deal with the raw text rather than throwing inside the transform.
        return data;
      }
    },
  ],
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(normalizeApiError(error)),
);
