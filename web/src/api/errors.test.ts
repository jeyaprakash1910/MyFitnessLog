import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { ApiError, normalizeApiError } from './errors';
import type { ApiErrorBody } from './types';

function axiosErrorWithResponse(status: number, data: unknown): AxiosError {
  const error = new AxiosError('boom', 'ERR_BAD_RESPONSE');
  error.response = {
    status,
    statusText: '',
    data,
    headers: {},
    config: { headers: new AxiosHeaders() },
  };
  return error;
}

const envelope: ApiErrorBody = {
  timestamp: '2026-07-22T00:00:00Z',
  status: 404,
  error: 'Not Found',
  message: 'Workout session not found.',
  path: '/api/v1/workout-sessions/x',
};

describe('normalizeApiError', () => {
  it('classifies a 4xx with an envelope as a client error and surfaces the message', () => {
    const result = normalizeApiError(axiosErrorWithResponse(404, envelope));
    expect(result).toBeInstanceOf(ApiError);
    expect(result.kind).toBe('client');
    expect(result.status).toBe(404);
    expect(result.isNotFound).toBe(true);
    expect(result.message).toBe('Workout session not found.');
    expect(result.body).toEqual(envelope);
  });

  it('classifies a 5xx as a server error', () => {
    const result = normalizeApiError(axiosErrorWithResponse(500, envelope));
    expect(result.kind).toBe('server');
    expect(result.status).toBe(500);
  });

  it('falls back to a generic message when no envelope is present', () => {
    const result = normalizeApiError(axiosErrorWithResponse(400, 'not json'));
    expect(result.kind).toBe('client');
    expect(result.message).toBe('Request failed with status 400.');
    expect(result.body).toBeUndefined();
  });

  it('classifies a response-less Axios error as a network error', () => {
    const result = normalizeApiError(new AxiosError('Network Error', 'ERR_NETWORK'));
    expect(result.kind).toBe('network');
    expect(result.status).toBeUndefined();
  });

  it('wraps a non-Axios throwable as unknown', () => {
    const result = normalizeApiError(new Error('nope'));
    expect(result.kind).toBe('unknown');
  });

  it('returns an existing ApiError unchanged', () => {
    const original = new ApiError('server', 'x', { status: 503 });
    expect(normalizeApiError(original)).toBe(original);
  });
});
