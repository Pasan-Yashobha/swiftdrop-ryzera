export const DELIVERY_STATUSES = ['PENDING', 'RETRYING', 'DELIVERED', 'FAILED'] as const;
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

/** One row of the webhook delivery log (GET /admin/webhooks/deliveries). */
export interface WebhookDelivery {
  id: string;
  merchantId: string;
  eventId: string;
  eventType: string;
  url: string;
  status: DeliveryStatus;
  attempts: number;
  lastHttpStatus: number | null;
  durationMs: number | null;
  createdAt: string;
  lastAttemptAt: string | null;
  nextAttemptAt: string | null;
  error: string | null;
  replayCount: number;
}

export type AttemptOutcome = 'RETRY' | 'SUCCESS' | 'FAILED';

export interface DeliveryAttempt {
  attemptNo: number;
  attemptedAt: string;
  httpStatus: number | null;
  durationMs: number | null;
  outcome: AttemptOutcome;
  error: string | null;
}

/** GET /admin/webhooks/deliveries/{id}: the row plus what was sent and every attempt. */
export interface WebhookDeliveryDetail extends WebhookDelivery {
  requestHeaders: Record<string, string>;
  /** The raw JSON body that was signed and sent. */
  payload: string;
  responseSnippet: string | null;
  lastReplayedBy: string | null;
  attemptLog: DeliveryAttempt[];
}

export interface DeliveryFilters {
  status?: DeliveryStatus | null;
  merchantId?: string | null;
  eventType?: string | null;
}

export interface DeliveryQuery extends DeliveryFilters {
  page: number;
  size: number;
}
