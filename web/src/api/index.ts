/** Public surface of the API layer. */
export { apiClient } from './client';
export { ApiError, normalizeApiError, type ApiErrorKind } from './errors';
export { parseJsonPreservingDecimals, asDecimalString } from './decimal';
export { fetchWorkoutHistory, fetchWorkoutDetail, fetchHealth } from './endpoints';
export type * from './types';
