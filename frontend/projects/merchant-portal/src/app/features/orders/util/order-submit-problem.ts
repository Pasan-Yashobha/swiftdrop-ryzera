import { AppError } from '../../../core/http/app-error';

/** How a failed order submission is explained to the merchant. */
export interface SubmitProblem {
  message: string;
  /** Submitting the same order again is safe (the same Idempotency-Key is reused) and may succeed. */
  retryable: boolean;
}

export const DUPLICATE_REF_MESSAGE =
  'You already have an order with this reference. Use a different reference or leave it empty.';

/** Friendly wording for the order-creation errors of CONTRACT section 4. */
export function describeSubmitError(error: AppError): SubmitProblem {
  switch (error.code) {
    case 'DUPLICATE_EXTERNAL_REF':
      return { message: DUPLICATE_REF_MESSAGE, retryable: false };
    case 'PARCEL_REJECTED':
      return {
        message: `The delivery service could not accept this parcel: ${error.message.replace(/[.\s]+$/, '')}. Check the addresses and parcel details, then try again.`,
        retryable: false,
      };
    case 'PARCEL_SERVICE_UNAVAILABLE':
      return {
        message:
          'The delivery service is not available right now, so your order was not created. Please try again in a moment.',
        retryable: true,
      };
    case 'IDEMPOTENCY_KEY_IN_USE':
      return {
        message:
          'Your previous attempt is still being processed. Wait a few seconds, then try again.',
        retryable: true,
      };
    case 'VALIDATION_FAILED':
      return {
        message: 'Some details are not valid. Fix the highlighted fields and try again.',
        retryable: false,
      };
    default:
      return { message: error.message, retryable: error.isServerError || error.status === 0 };
  }
}
