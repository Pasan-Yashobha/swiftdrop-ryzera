import { AuthSession, UserRole } from '../models/auth.model';
import {
  NotificationLog,
  NotificationTemplate,
  SmsInboxMessage,
} from '../models/notification.model';
import { PageResponse } from '../models/page.model';
import { WebhookDelivery, WebhookDeliveryDetail } from '../models/webhook.model';

/** Builders for the DTOs the specs share, so each spec only states what it cares about. */
export function aSession(
  overrides: Partial<AuthSession> = {},
  role: UserRole = 'ADMIN',
): AuthSession {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    expiresIn: 3600,
    user: {
      id: 'admin-1',
      email: 'ops@swiftdrop.example',
      fullName: 'Kasun Silva',
      role,
    },
    ...overrides,
  };
}

export function aPage<T>(content: T[], overrides: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    ...overrides,
  };
}

export function aNotification(overrides: Partial<NotificationLog> = {}): NotificationLog {
  return {
    id: 'n-1',
    eventId: 'e-1',
    eventType: 'parcel.status.out_for_delivery',
    channel: 'EMAIL',
    recipient: 'k***@gmail.com',
    templateKey: 'parcel.status.out_for_delivery.EMAIL',
    subject: 'Your parcel is out for delivery',
    status: 'SENT',
    attempts: 1,
    lastError: null,
    createdAt: '2026-11-14T08:00:00Z',
    sentAt: '2026-11-14T08:00:02Z',
    ...overrides,
  };
}

export function anSms(overrides: Partial<SmsInboxMessage> = {}): SmsInboxMessage {
  return {
    id: 'sms-1',
    to: '07X XXX 4567',
    message: 'SwiftDrop: parcel SD-1234 is out for delivery.',
    receivedAt: '2026-11-14T08:00:03Z',
    ...overrides,
  };
}

export function aTemplate(overrides: Partial<NotificationTemplate> = {}): NotificationTemplate {
  return {
    id: 't-1',
    eventType: 'parcel.status.out_for_delivery',
    channel: 'EMAIL',
    subject: 'Your parcel {{trackingNumber}} is on its way',
    body: 'Hi {{customerName}}, parcel {{trackingNumber}} is out for delivery.',
    active: true,
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export function aDelivery(overrides: Partial<WebhookDelivery> = {}): WebhookDelivery {
  return {
    id: 'd-1',
    merchantId: 'm-1',
    eventId: 'e-1',
    eventType: 'parcel.status.delivered',
    url: 'https://shop.example/hooks/swiftdrop',
    status: 'DELIVERED',
    attempts: 1,
    lastHttpStatus: 200,
    durationMs: 120,
    createdAt: '2026-11-14T08:00:00Z',
    lastAttemptAt: '2026-11-14T08:00:01Z',
    nextAttemptAt: null,
    error: null,
    replayCount: 0,
    ...overrides,
  };
}

export function aDeliveryDetail(
  overrides: Partial<WebhookDeliveryDetail> = {},
): WebhookDeliveryDetail {
  return {
    ...aDelivery(),
    requestHeaders: {
      'X-SwiftDrop-Event-Id': 'e-1',
      'X-SwiftDrop-Signature': 't=1,v1=abc',
    },
    payload: '{"id":"e-1","type":"parcel.status.delivered","data":{"trackingNumber":"SD-1"}}',
    responseSnippet: 'ok',
    lastReplayedBy: null,
    attemptLog: [
      {
        attemptNo: 1,
        attemptedAt: '2026-11-14T08:00:01Z',
        httpStatus: 200,
        durationMs: 120,
        outcome: 'SUCCESS',
        error: null,
      },
    ],
    ...overrides,
  };
}
