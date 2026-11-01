import { WEBHOOK_EVENT_TYPES } from '../../../core/models/webhook.model';
import {
  DOC_SECTIONS,
  ERROR_CODES,
  ORDER_FIELDS,
  ORDER_STATUS_DOCS,
  WEBHOOK_EVENT_DOCS,
  WEBHOOK_HEADERS,
  WEBHOOK_RETRY_DELAYS,
} from './api-docs.content';
import {
  CREATE_ORDER_BODY,
  CREATE_ORDER_CURL,
  CREATE_ORDER_RESPONSE,
  ERROR_RESPONSE,
  SIGNATURE_VERIFICATION_SAMPLES,
  TOKEN_ERROR_RESPONSE,
  TOKEN_RESPONSE,
  WEBHOOK_PAYLOAD,
} from './api-docs.samples';

describe('API documentation content', () => {
  it('has unique section ids for the table of contents', () => {
    const ids = DOC_SECTIONS.map((section) => section.id);

    expect(new Set(ids).size).toBe(ids.length);
    expect(ids).toContain('verify-signatures');
  });

  it('documents exactly the webhook events a merchant can subscribe to', () => {
    expect(WEBHOOK_EVENT_TYPES).toEqual([
      'parcel.created',
      'parcel.status.picked_up',
      'parcel.status.in_transit',
      'parcel.status.out_for_delivery',
      'parcel.status.delivered',
      'parcel.status.failed',
      'parcel.status.rescheduled',
      'parcel.status.returned',
      'parcel.cancelled',
    ]);
    expect(WEBHOOK_EVENT_DOCS.map((event) => event.type)).toEqual([...WEBHOOK_EVENT_TYPES]);
    expect(WEBHOOK_EVENT_DOCS.every((event) => event.description.length > 0)).toBeTrue();
  });

  it('lists every error code once, in HTTP status order', () => {
    const codes = ERROR_CODES.map((item) => item.code);
    const statuses = ERROR_CODES.map((item) => item.status);

    expect(new Set(codes).size).toBe(codes.length);
    expect(statuses).toEqual([...statuses].sort((a, b) => a - b));
    expect(codes).toEqual(
      jasmine.arrayContaining([
        'VALIDATION_FAILED',
        'INVALID_API_KEY',
        'MERCHANT_NOT_APPROVED',
        'SANDBOX_ONLY',
        'DUPLICATE_EXTERNAL_REF',
        'IDEMPOTENCY_KEY_IN_USE',
        'IDEMPOTENCY_KEY_REUSED',
        'PARCEL_REJECTED',
        'RATE_LIMITED',
        'INTERNAL_ERROR',
        'PARCEL_SERVICE_UNAVAILABLE',
      ]),
    );
  });

  it('documents every field of the order request', () => {
    const fields = ORDER_FIELDS.map((field) => field.name);

    expect(fields).toEqual(
      jasmine.arrayContaining([
        'externalRef',
        'recipient.name',
        'recipient.phone',
        'pickup.addressLine',
        'dropoff.city',
        'parcel.weightKg',
        'parcel.size',
        'notes',
      ]),
    );
    const request = JSON.parse(CREATE_ORDER_BODY) as Record<string, unknown>;
    expect(Object.keys(request)).toEqual([
      'externalRef',
      'recipient',
      'pickup',
      'dropoff',
      'parcel',
      'notes',
    ]);
  });

  it('describes every order status of the lifecycle', () => {
    expect(ORDER_STATUS_DOCS.map((item) => item.status)).toEqual([
      'CREATED',
      'ASSIGNED',
      'PICKED_UP',
      'IN_TRANSIT',
      'OUT_FOR_DELIVERY',
      'DELIVERED',
      'FAILED',
      'RESCHEDULED',
      'RETURNED',
      'CANCELLED',
    ]);
  });

  it('documents the four webhook headers and the retry schedule', () => {
    expect(WEBHOOK_HEADERS.map((item) => item.header)).toEqual([
      'X-SwiftDrop-Event-Id',
      'X-SwiftDrop-Event-Type',
      'X-SwiftDrop-Timestamp',
      'X-SwiftDrop-Signature',
    ]);
    expect(WEBHOOK_RETRY_DELAYS).toEqual([
      '5 seconds',
      '30 seconds',
      '2 minutes',
      '10 minutes',
      '30 minutes',
    ]);
  });

  it('only contains JSON samples that parse', () => {
    for (const sample of [
      CREATE_ORDER_BODY,
      CREATE_ORDER_RESPONSE,
      TOKEN_RESPONSE,
      TOKEN_ERROR_RESPONSE,
      ERROR_RESPONSE,
      WEBHOOK_PAYLOAD,
    ]) {
      expect(() => JSON.parse(sample)).not.toThrow();
    }
    expect(JSON.parse(CREATE_ORDER_RESPONSE).status).toBe('CREATED');
    expect(JSON.parse(TOKEN_RESPONSE).token_type).toBe('Bearer');
    expect(JSON.parse(ERROR_RESPONSE).code).toBe('VALIDATION_FAILED');
    expect(JSON.parse(WEBHOOK_PAYLOAD).eventType).toBe('parcel.status.in_transit');
  });

  it('sends the create-order example with an idempotency key and an API key placeholder', () => {
    expect(CREATE_ORDER_CURL).toContain('-H "Idempotency-Key: ');
    expect(CREATE_ORDER_CURL).toContain('-H "X-API-Key: YOUR_API_KEY"');
    expect(CREATE_ORDER_CURL).toContain('{{BASE_URL}}/merchant-api/orders');
  });
});

describe('Signature verification samples', () => {
  const byId = (id: string) =>
    SIGNATURE_VERIFICATION_SAMPLES.find((sample) => sample.id === id)?.code ?? '';

  it('cover Node.js, Python and Java in that order', () => {
    expect(SIGNATURE_VERIFICATION_SAMPLES.map((sample) => sample.label)).toEqual([
      'Node.js',
      'Python',
      'Java',
    ]);
  });

  it('read the signature, timestamp and event id headers and sign timestamp + "." + body with HMAC-SHA256', () => {
    for (const { code } of SIGNATURE_VERIFICATION_SAMPLES) {
      expect(code).toContain('X-SwiftDrop-Signature');
      expect(code).toContain('X-SwiftDrop-Timestamp');
      expect(code).toContain('X-SwiftDrop-Event-Id');
      expect(code.toLowerCase()).toContain('sha256');
    }
    expect(byId('node')).toContain(".update(timestamp + '.')");
    expect(byId('python')).toContain('timestamp.encode() + b"." + raw_body');
    expect(byId('java')).toContain('(timestamp + ".")');
  });

  it('compare the signature in constant time', () => {
    expect(byId('node')).toContain('crypto.timingSafeEqual');
    expect(byId('python')).toContain('hmac.compare_digest');
    expect(byId('java')).toContain('MessageDigest.isEqual');
    for (const { code } of SIGNATURE_VERIFICATION_SAMPLES) {
      expect(code).not.toMatch(
        /(signature\s*(===?|!=)\s*expected|expected\s*(===?|!=)\s*signature)/,
      );
    }
  });

  it('reject timestamps older than five minutes', () => {
    expect(byId('node')).toContain('MAX_AGE_SECONDS = 5 * 60');
    expect(byId('python')).toContain('MAX_AGE_SECONDS = 5 * 60');
    expect(byId('java')).toContain('Duration.ofMinutes(5)');
  });

  it('ignore events that were already processed and point at the out-of-order rule', () => {
    for (const { code } of SIGNATURE_VERIFICATION_SAMPLES) {
      expect(code).toMatch(/seen[_]?[eE]vent[_]?[iI]ds/);
      expect(code).toContain('occurredAt');
    }
  });

  it('verify against the raw body, before any JSON parsing', () => {
    expect(byId('node')).toContain("express.raw({ type: 'application/json' })");
    expect(byId('python')).toContain('request.get_data()');
    expect(byId('java')).toContain('@RequestBody byte[] rawBody');
  });

  it('never contain a real looking secret', () => {
    for (const { code } of SIGNATURE_VERIFICATION_SAMPLES) {
      expect(code).not.toMatch(/whsec_[A-Za-z0-9_-]{20,}/);
    }
  });
});
