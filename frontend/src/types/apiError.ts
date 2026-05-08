/**
 * Structured error type for API responses.
 * Use this instead of `any` when catching errors from API calls.
 */
export interface ApiError {
  status?: number;
  message: string;
  body?: {
    message?: string;
    [key: string]: unknown;
  };
}

/**
 * Extracts a user-friendly message from an unknown error.
 */
export function getErrorMessage(error: unknown, fallback = 'An unexpected error occurred'): string {
  if (error && typeof error === 'object') {
    const err = error as ApiError;
    return err.body?.message || err.message || fallback;
  }
  if (typeof error === 'string') return error;
  return fallback;
}
