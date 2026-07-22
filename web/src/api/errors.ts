import { AxiosError } from 'axios';
import type { ApiErrorBody } from './types';

/**
 * The single error type the rest of the app sees.
 *
 * Axios throws a sprawling `AxiosError`; every consumer having to re-derive
 * "was this a 404, a network drop, or a 500?" from it is how error handling
 * drifts. The API layer converts every failure into one of these at the
 * boundary (see `normalizeApiError`), so pages branch on a small, stable shape.
 */
export type ApiErrorKind =
  /** The request never got a response: offline, DNS, timeout, CORS preflight rejected. */
  | 'network'
  /** The server answered with a 4xx. `status` and the envelope are populated. */
  | 'client'
  /** The server answered with a 5xx. */
  | 'server'
  /** Anything not otherwise classifiable — a bug in request setup, usually. */
  | 'unknown';

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  /** HTTP status when there was a response; `undefined` for a network error. */
  readonly status: number | undefined;
  /** The backend's error envelope when it sent one. */
  readonly body: ApiErrorBody | undefined;

  constructor(
    kind: ApiErrorKind,
    message: string,
    // `| undefined` on each is deliberate: with exactOptionalPropertyTypes,
    // callers legitimately pass an explicitly-undefined body/status for a
    // network error, and that must be allowed.
    options: {
      status?: number | undefined;
      body?: ApiErrorBody | undefined;
      cause?: unknown;
    } = {},
  ) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = 'ApiError';
    this.kind = kind;
    this.status = options.status;
    this.body = options.body;
  }

  /** True for a 404 — the common "unknown workout id" case the detail page handles. */
  get isNotFound(): boolean {
    return this.status === 404;
  }
}

function looksLikeErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    'message' in value &&
    typeof (value as { message: unknown }).message === 'string'
  );
}

/**
 * Converts any thrown value from an Axios call into an {@link ApiError}.
 *
 * Kept pure and exported so it can be unit-tested directly against synthetic
 * `AxiosError`s without a live server.
 */
export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }

  if (error instanceof AxiosError) {
    if (error.response) {
      const status = error.response.status;
      const body = looksLikeErrorBody(error.response.data) ? error.response.data : undefined;
      const message = body?.message ?? `Request failed with status ${status}.`;
      const kind: ApiErrorKind = status >= 500 ? 'server' : 'client';
      return new ApiError(kind, message, { status, body, cause: error });
    }
    // No response object → the request never completed.
    return new ApiError('network', 'Could not reach the server. Check your connection.', {
      cause: error,
    });
  }

  return new ApiError('unknown', 'An unexpected error occurred.', { cause: error });
}
