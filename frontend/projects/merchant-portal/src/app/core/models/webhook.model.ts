import { PageResponse } from './page.model';

/** Event types a merchant can subscribe to; `*` (ALL_WEBHOOK_EVENTS) means all of them. */
export const WEBHOOK_EVENT_TYPES = [
  'parcel.created',
  'parcel.assigned',
  'parcel.status.picked_up',
  'parcel.status.in_transit',
  'parcel.status.out_for_delivery',
  'parcel.status.delivered',
  'parcel.status.failed',
  'parcel.status.rescheduled',
  'parcel.status.returned',
  'parcel.cancelled',
] as const;

export const ALL_WEBHOOK_EVENTS = '*';

export interface WebhookNotConfigured {
  configured: false;
}

/** Webhook settings as read. The secret is never part of this response. */
export interface WebhookConfigured {
  configured: true;
  url: string;
  active: boolean;
  events: string[];
  secretHint: string;
  lastTestAt: string | null;
  lastTestStatus: string | null;
}

export type WebhookConfig = WebhookNotConfigured | WebhookConfigured;

export interface SaveWebhookRequest {
  url: string;
  active: boolean;
  /** `['*']` or a list of WEBHOOK_EVENT_TYPES. */
  events: string[];
}

/** Response of PUT /webhook. `secret` is present only on the call that created the configuration. */
export type SavedWebhook = WebhookConfigured & { secret?: string };

export interface WebhookSecret {
  secret: string;
}

export interface WebhookTestResult {
  delivered: boolean;
  httpStatus: number | null;
  durationMs: number;
  error: string | null;
}

export interface WebhookDelivery {
  id: string;
  eventId: string;
  eventType: string;
  status: string;
  attempts: number;
  lastHttpStatus: number | null;
  durationMs: number | null;
  createdAt: string;
  lastAttemptAt: string | null;
  error: string | null;
}

/** `available` is false when the delivery log service cannot be reached; the list is then empty. */
export interface WebhookDeliveryPage extends PageResponse<WebhookDelivery> {
  available: boolean;
}
