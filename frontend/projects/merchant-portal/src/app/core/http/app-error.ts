import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, ApiFieldError } from '../models/api-error.model';

const CORRELATION_HEADER = 'X-Correlation-Id';

const FALLBACK_MESSAGES: Readonly<Record<number, string>> = {
  0: 'We could not reach SwiftDrop. Check your internet connection and try again.',
  400: 'The request was not valid. Check the details and try again.',
  401: 'Your session has expired. Please sign in again.',
  403: 'You do not have permission to do that.',
  404: 'We could not find what you were looking for.',
  409: 'That change conflicts with the current state. Refresh and try again.',
  413: 'The upload is too large.',
  422: 'The request could not be processed.',
  429: 'Too many requests. Please wait a moment and try again.',
  503: 'SwiftDrop is temporarily unavailable. Please try again shortly.',
};

const GENERIC_SERVER_MESSAGE = 'Something went wrong on our side. Please try again.';
const GENERIC_CLIENT_MESSAGE = 'The request could not be completed.';

/**
 * The only error type the UI deals with. Built from the contract's error JSON (CONTRACT section 4);
 * the message is always safe to show to a user.
 */
export class AppError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details: readonly ApiFieldError[] = [],
    readonly correlationId: string | null = null,
  ) {
    super(message);
    this.name = 'AppError';
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }

  /** First validation message the server reported for a field (dotted path such as `dropoff.city`). */
  fieldMessage(field: string): string | undefined {
    return this.details.find((detail) => detail.field === field)?.message;
  }
}

export function isAppError(value: unknown): value is AppError {
  return value instanceof AppError;
}

/** Maps an HTTP failure to an AppError. Server text is only trusted for 4xx responses. */
export function fromHttpError(response: HttpErrorResponse): AppError {
  const body = asApiError(response.error);
  const correlationId = body?.correlationId ?? response.headers?.get(CORRELATION_HEADER) ?? null;

  if (response.status >= 500) {
    return new AppError(
      response.status,
      body?.code ?? 'INTERNAL_ERROR',
      FALLBACK_MESSAGES[response.status] ?? GENERIC_SERVER_MESSAGE,
      [],
      correlationId,
    );
  }

  const fallbackCode = response.status === 0 ? 'NETWORK_ERROR' : 'REQUEST_FAILED';
  return new AppError(
    response.status,
    body?.code ?? fallbackCode,
    body?.message || FALLBACK_MESSAGES[response.status] || GENERIC_CLIENT_MESSAGE,
    body?.details ?? [],
    correlationId,
  );
}

/** Normalises anything a failed observable can emit, so components can rely on getting an AppError. */
export function toAppError(error: unknown): AppError {
  if (isAppError(error)) {
    return error;
  }
  if (error instanceof HttpErrorResponse) {
    return fromHttpError(error);
  }
  return new AppError(0, 'UNEXPECTED_ERROR', GENERIC_SERVER_MESSAGE);
}

function asApiError(body: unknown): Partial<ApiError> | null {
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const candidate = body as Record<string, unknown>;
  if (typeof candidate['message'] !== 'string' && typeof candidate['detail'] === 'string') {
    // Spring ProblemDetail (RFC 9457), used by the Pricing, Document and Assistant services.
    return {
      code: typeof candidate['code'] === 'string' ? candidate['code'] : 'REQUEST_FAILED',
      message: candidate['detail'],
      details: [],
    };
  }
  if (typeof candidate['code'] !== 'string' || typeof candidate['message'] !== 'string') {
    return null;
  }
  return {
    code: candidate['code'],
    message: candidate['message'],
    correlationId:
      typeof candidate['correlationId'] === 'string' ? candidate['correlationId'] : undefined,
    details: parseDetails(candidate['details']),
  };
}

function parseDetails(value: unknown): ApiFieldError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(
    (item): item is ApiFieldError =>
      typeof item === 'object' &&
      item !== null &&
      typeof (item as ApiFieldError).field === 'string' &&
      typeof (item as ApiFieldError).message === 'string',
  );
}
