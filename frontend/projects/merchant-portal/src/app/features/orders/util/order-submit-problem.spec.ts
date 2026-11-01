import { AppError } from '../../../core/http/app-error';
import { DUPLICATE_REF_MESSAGE, describeSubmitError } from './order-submit-problem';

describe('describeSubmitError', () => {
  it('explains a duplicate external reference', () => {
    const problem = describeSubmitError(new AppError(409, 'DUPLICATE_EXTERNAL_REF', 'dup'));

    expect(problem).toEqual({ message: DUPLICATE_REF_MESSAGE, retryable: false });
  });

  it('quotes the reason a parcel was rejected without doubling the full stop', () => {
    const problem = describeSubmitError(
      new AppError(422, 'PARCEL_REJECTED', 'Destination is outside the service area.'),
    );

    expect(problem.message).toBe(
      'The delivery service could not accept this parcel: Destination is outside the service area. Check the addresses and parcel details, then try again.',
    );
    expect(problem.retryable).toBeFalse();
  });

  it('offers a retry when the delivery service is unavailable or the key is still in use', () => {
    const unavailable = describeSubmitError(new AppError(503, 'PARCEL_SERVICE_UNAVAILABLE', 'x'));
    const inUse = describeSubmitError(new AppError(409, 'IDEMPOTENCY_KEY_IN_USE', 'x'));

    expect(unavailable.retryable).toBeTrue();
    expect(unavailable.message).toContain('your order was not created');
    expect(inUse.retryable).toBeTrue();
    expect(inUse.message).toContain('still being processed');
  });

  it('points to the highlighted fields for validation errors', () => {
    const problem = describeSubmitError(new AppError(400, 'VALIDATION_FAILED', 'bad'));

    expect(problem.message).toContain('highlighted fields');
    expect(problem.retryable).toBeFalse();
  });

  it('shows the server message for other errors and retries only transient ones', () => {
    expect(describeSubmitError(new AppError(403, 'FORBIDDEN', 'No access'))).toEqual({
      message: 'No access',
      retryable: false,
    });
    expect(describeSubmitError(new AppError(500, 'INTERNAL_ERROR', 'Oops')).retryable).toBeTrue();
    expect(describeSubmitError(new AppError(0, 'NETWORK_ERROR', 'Offline')).retryable).toBeTrue();
  });
});
