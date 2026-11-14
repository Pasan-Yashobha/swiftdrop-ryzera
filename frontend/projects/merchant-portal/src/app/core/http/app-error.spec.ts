import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { AppError, fromHttpError, isAppError, toAppError } from './app-error';

function failure(
  status: number,
  body: unknown,
  headers?: Record<string, string>,
): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });
}

describe('fromHttpError', () => {
  it('maps the contract error body, keeping the safe message, code, details and correlation id', () => {
    const error = fromHttpError(
      failure(400, {
        status: 400,
        code: 'VALIDATION_FAILED',
        message: 'Validation failed',
        correlationId: 'abc-123',
        details: [{ field: 'dropoff.city', message: 'must not be blank' }],
      }),
    );

    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_FAILED');
    expect(error.message).toBe('Validation failed');
    expect(error.correlationId).toBe('abc-123');
    expect(error.fieldMessage('dropoff.city')).toBe('must not be blank');
    expect(error.fieldMessage('other')).toBeUndefined();
  });

  it('never exposes server text for 5xx responses', () => {
    const error = fromHttpError(
      failure(500, {
        code: 'INTERNAL_ERROR',
        message: 'java.lang.NullPointerException at Foo.bar(Foo.java:12)',
      }),
    );

    expect(error.isServerError).toBeTrue();
    expect(error.message).not.toContain('NullPointer');
    expect(error.message).toBe('Something went wrong on our side. Please try again.');
  });

  it('uses a friendly fixed message for 503', () => {
    const error = fromHttpError(
      failure(503, { code: 'PARCEL_SERVICE_UNAVAILABLE', message: 'upstream said no' }),
    );

    expect(error.code).toBe('PARCEL_SERVICE_UNAVAILABLE');
    expect(error.message).toContain('temporarily unavailable');
  });

  it('describes a network failure', () => {
    const error = fromHttpError(failure(0, new ProgressEvent('error')));

    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.message).toContain('could not reach');
  });

  it('falls back to a status message when the 4xx body is not the contract shape', () => {
    const error = fromHttpError(failure(403, '<html>Forbidden</html>'));

    expect(error.code).toBe('REQUEST_FAILED');
    expect(error.message).toBe('You do not have permission to do that.');
  });

  it('reads the safe detail sentence from a Spring ProblemDetail body', () => {
    const error = fromHttpError(
      failure(409, {
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'A different receipt already exists for this parcel',
      }),
    );

    expect(error.code).toBe('REQUEST_FAILED');
    expect(error.message).toBe('A different receipt already exists for this parcel');
  });

  it('still hides ProblemDetail text for 5xx responses', () => {
    const error = fromHttpError(failure(502, { title: 'Bad Gateway', detail: 'upstream trace' }));

    expect(error.message).not.toContain('upstream trace');
  });

  it('ignores malformed details entries', () => {
    const error = fromHttpError(
      failure(400, {
        code: 'VALIDATION_FAILED',
        message: 'bad',
        details: [{ field: 'a', message: 'b' }, 'oops', { field: 1 }],
      }),
    );

    expect(error.details).toEqual([{ field: 'a', message: 'b' }]);
  });

  it('reads the correlation id from the response header when the body has none', () => {
    const error = fromHttpError(failure(500, null, { 'X-Correlation-Id': 'from-header' }));

    expect(error.correlationId).toBe('from-header');
  });
});

describe('toAppError', () => {
  it('returns an AppError untouched', () => {
    const original = new AppError(404, 'NOT_FOUND', 'nope');

    expect(toAppError(original)).toBe(original);
    expect(isAppError(original)).toBeTrue();
  });

  it('converts an HttpErrorResponse', () => {
    expect(toAppError(failure(404, null)).status).toBe(404);
  });

  it('wraps anything else in a generic error', () => {
    const error = toAppError(new TypeError('boom'));

    expect(error.code).toBe('UNEXPECTED_ERROR');
    expect(error.message).not.toContain('boom');
  });
});
