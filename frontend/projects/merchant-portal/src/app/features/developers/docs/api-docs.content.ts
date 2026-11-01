import { OrderStatus } from '../../../core/models/order.model';
import { WEBHOOK_EVENT_TYPES } from '../../../core/models/webhook.model';

/** Public path of the OpenAPI document served by the merchant service. */
export const OPENAPI_PATH = '/api/v1/merchant-api/openapi.yaml';

export interface DocSection {
  /** Used as the heading id and the URL fragment. */
  id: string;
  title: string;
}

/** The sections of the guide, in reading order. The table of contents is built from this list. */
export const DOC_SECTIONS: readonly DocSection[] = [
  { id: 'overview', title: 'Overview' },
  { id: 'quick-start', title: 'Quick start' },
  { id: 'environments', title: 'Live and test environments' },
  { id: 'authentication', title: 'Authentication' },
  { id: 'create-order', title: 'Creating an order' },
  { id: 'retrieve-orders', title: 'Retrieving orders' },
  { id: 'lifecycle', title: 'Order status lifecycle' },
  { id: 'errors', title: 'Errors' },
  { id: 'rate-limits', title: 'Rate limits' },
  { id: 'webhooks', title: 'Webhooks' },
  { id: 'verify-signatures', title: 'Verifying webhook signatures' },
];

export interface FieldDoc {
  name: string;
  type: string;
  required: boolean;
  rules: string;
}

export const ORDER_FIELDS: readonly FieldDoc[] = [
  {
    name: 'externalRef',
    type: 'string',
    required: false,
    rules:
      'Your own order reference. 1 to 100 characters from A-Z a-z 0-9 . _ : / # -. Unique per merchant; a repeat is rejected with DUPLICATE_EXTERNAL_REF.',
  },
  { name: 'recipient.name', type: 'string', required: true, rules: '1 to 120 characters.' },
  {
    name: 'recipient.phone',
    type: 'string',
    required: true,
    rules:
      '9 to 15 digits with an optional leading +, for example +94771234567. Spaces, dashes and brackets are stripped.',
  },
  { name: 'pickup.addressLine', type: 'string', required: true, rules: '1 to 300 characters.' },
  { name: 'pickup.city', type: 'string', required: true, rules: '1 to 100 characters.' },
  { name: 'pickup.postalCode', type: 'string', required: false, rules: 'Up to 16 characters.' },
  { name: 'dropoff.addressLine', type: 'string', required: true, rules: '1 to 300 characters.' },
  { name: 'dropoff.city', type: 'string', required: true, rules: '1 to 100 characters.' },
  { name: 'dropoff.postalCode', type: 'string', required: false, rules: 'Up to 16 characters.' },
  {
    name: 'parcel.weightKg',
    type: 'number',
    required: true,
    rules: '0.01 to 50.00 kilograms, at most 2 decimals.',
  },
  {
    name: 'parcel.size',
    type: 'string',
    required: true,
    rules: 'One of SMALL, MEDIUM or LARGE.',
  },
  { name: 'parcel.description', type: 'string', required: false, rules: 'Up to 200 characters.' },
  { name: 'notes', type: 'string', required: false, rules: 'Up to 500 characters.' },
];

export interface StatusDoc {
  status: OrderStatus;
  meaning: string;
}

export const ORDER_STATUS_DOCS: readonly StatusDoc[] = [
  { status: 'CREATED', meaning: 'SwiftDrop accepted the order and a tracking number exists.' },
  { status: 'ASSIGNED', meaning: 'A courier has been assigned to collect the parcel.' },
  { status: 'PICKED_UP', meaning: 'The courier collected the parcel from the pickup address.' },
  { status: 'IN_TRANSIT', meaning: 'The parcel is on its way to the delivery area.' },
  { status: 'OUT_FOR_DELIVERY', meaning: 'The courier is delivering the parcel today.' },
  { status: 'DELIVERED', meaning: 'The parcel reached the recipient. Final status.' },
  {
    status: 'FAILED',
    meaning:
      'The delivery attempt failed. failureReason says why; the parcel is rescheduled or returned.',
  },
  { status: 'RESCHEDULED', meaning: 'Another delivery attempt is planned after a failed one.' },
  {
    status: 'RETURNED',
    meaning: 'The parcel is being or has been returned to the pickup address.',
  },
  { status: 'CANCELLED', meaning: 'The order was cancelled before delivery. Final status.' },
];

export interface ErrorCodeDoc {
  code: string;
  status: number;
  meaning: string;
}

/** Every `code` the API can return, ordered by HTTP status. */
export const ERROR_CODES: readonly ErrorCodeDoc[] = [
  {
    code: 'VALIDATION_FAILED',
    status: 400,
    meaning: 'A field is missing or invalid. The details array lists each field.',
  },
  {
    code: 'MALFORMED_REQUEST',
    status: 400,
    meaning: 'The body is not valid JSON or a value has the wrong type.',
  },
  { code: 'UNAUTHENTICATED', status: 401, meaning: 'No credential was sent.' },
  { code: 'INVALID_API_KEY', status: 401, meaning: 'The API key is unknown, revoked or expired.' },
  {
    code: 'INVALID_TOKEN',
    status: 401,
    meaning: 'The access token is malformed, expired or its client was revoked.',
  },
  {
    code: 'FORBIDDEN',
    status: 403,
    meaning: 'The credential does not have the scope this endpoint needs.',
  },
  {
    code: 'MERCHANT_NOT_APPROVED',
    status: 403,
    meaning: 'Your merchant account is not approved (pending, rejected or suspended).',
  },
  { code: 'SANDBOX_ONLY', status: 403, meaning: 'The endpoint only accepts test credentials.' },
  {
    code: 'NOT_FOUND',
    status: 404,
    meaning: 'The order does not exist or belongs to another merchant.',
  },
  {
    code: 'MERCHANT_NOT_REGISTERED',
    status: 404,
    meaning: 'Portal only: no business has been registered for this user.',
  },
  {
    code: 'METHOD_NOT_ALLOWED',
    status: 405,
    meaning: 'The HTTP method is not supported on this path.',
  },
  {
    code: 'MERCHANT_ALREADY_REGISTERED',
    status: 409,
    meaning: 'Portal only: this user already registered a business.',
  },
  {
    code: 'DUPLICATE_EXTERNAL_REF',
    status: 409,
    meaning: 'An order with this externalRef already exists.',
  },
  {
    code: 'IDEMPOTENCY_KEY_IN_USE',
    status: 409,
    meaning:
      'A request with this Idempotency-Key is still running. Retry after the Retry-After seconds.',
  },
  {
    code: 'INVALID_STATE',
    status: 409,
    meaning:
      'The action is not allowed in the current state, for example advancing a delivered sandbox order.',
  },
  {
    code: 'OPTIMISTIC_LOCK',
    status: 409,
    meaning: 'The record changed at the same time. Read it again and retry.',
  },
  {
    code: 'WEBHOOK_NOT_CONFIGURED',
    status: 409,
    meaning: 'Portal only: set up a webhook before testing it.',
  },
  {
    code: 'PAYLOAD_TOO_LARGE',
    status: 413,
    meaning: 'The request body is larger than the allowed size.',
  },
  { code: 'UNSUPPORTED_MEDIA_TYPE', status: 415, meaning: 'Send the body as application/json.' },
  {
    code: 'IDEMPOTENCY_KEY_REUSED',
    status: 422,
    meaning: 'The Idempotency-Key was already used with a different payload.',
  },
  {
    code: 'PARCEL_REJECTED',
    status: 422,
    meaning:
      'The parcel service rejected the order, for example an address it cannot serve. Fix the data and retry.',
  },
  {
    code: 'WEBHOOK_URL_REJECTED',
    status: 422,
    meaning:
      'Portal only: the webhook URL is not allowed (not https, private address, credentials in the URL).',
  },
  {
    code: 'RATE_LIMITED',
    status: 429,
    meaning: 'Too many requests. Wait for the Retry-After seconds.',
  },
  {
    code: 'INTERNAL_ERROR',
    status: 500,
    meaning: 'Something went wrong on our side. Quote the correlationId when you contact support.',
  },
  {
    code: 'PARCEL_SERVICE_UNAVAILABLE',
    status: 503,
    meaning: 'The order could not be placed right now. Retry with the same Idempotency-Key.',
  },
];

export interface RateLimitHeaderDoc {
  header: string;
  meaning: string;
}

export const RATE_LIMIT_HEADERS: readonly RateLimitHeaderDoc[] = [
  {
    header: 'X-RateLimit-Limit',
    meaning: 'Requests allowed per minute for your merchant account.',
  },
  { header: 'X-RateLimit-Remaining', meaning: 'Requests left in the current window.' },
  {
    header: 'Retry-After',
    meaning: 'Only on 429 responses: seconds to wait before the next request.',
  },
];

const EVENT_DESCRIPTIONS: Readonly<Record<(typeof WEBHOOK_EVENT_TYPES)[number], string>> = {
  'parcel.created': 'A parcel was created for an order.',
  'parcel.status.picked_up': 'The courier collected the parcel.',
  'parcel.status.in_transit': 'The parcel is on its way.',
  'parcel.status.out_for_delivery': 'The parcel is out for delivery.',
  'parcel.status.delivered': 'The parcel was delivered.',
  'parcel.status.failed': 'A delivery attempt failed; data.failureReason says why.',
  'parcel.status.rescheduled': 'A new delivery attempt was scheduled.',
  'parcel.status.returned': 'The parcel is being returned to the sender.',
  'parcel.cancelled': 'The parcel was cancelled.',
};

export const WEBHOOK_EVENT_DOCS: readonly { type: string; description: string }[] =
  WEBHOOK_EVENT_TYPES.map((type) => ({ type, description: EVENT_DESCRIPTIONS[type] }));

export const WEBHOOK_HEADERS: readonly { header: string; meaning: string }[] = [
  {
    header: 'X-SwiftDrop-Event-Id',
    meaning:
      'Unique id of the event. The same id is sent again on every retry; use it to ignore duplicates.',
  },
  {
    header: 'X-SwiftDrop-Event-Type',
    meaning: 'The event type, for example parcel.status.delivered.',
  },
  {
    header: 'X-SwiftDrop-Timestamp',
    meaning: 'When the delivery was signed, as Unix time in seconds. Part of the signed content.',
  },
  {
    header: 'X-SwiftDrop-Signature',
    meaning: 'Hex HMAC-SHA256 of timestamp + "." + raw body, keyed with your signing secret.',
  },
];

/** Wait before each retry of a failed delivery, in order. */
export const WEBHOOK_RETRY_DELAYS: readonly string[] = [
  '5 seconds',
  '30 seconds',
  '2 minutes',
  '10 minutes',
  '30 minutes',
];
