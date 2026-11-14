export const NOTIFICATION_STATUSES = ['PENDING', 'SENT', 'FAILED', 'SKIPPED'] as const;
export type NotificationStatus = (typeof NOTIFICATION_STATUSES)[number];

export const NOTIFICATION_CHANNELS = ['EMAIL', 'SMS'] as const;
export type NotificationChannel = (typeof NOTIFICATION_CHANNELS)[number];

/** One row of the notification log (GET /admin/notifications). The recipient is masked by the server. */
export interface NotificationLog {
  id: string;
  eventId: string;
  eventType: string;
  channel: NotificationChannel;
  recipient: string;
  templateKey: string;
  subject: string | null;
  status: NotificationStatus;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

/** Filters of the notification log. Empty values are left out of the query string. */
export interface NotificationLogFilters {
  status?: NotificationStatus | null;
  channel?: NotificationChannel | null;
  eventType?: string | null;
  /** ISO-8601 instant, inclusive. */
  from?: string | null;
  /** ISO-8601 instant, inclusive. */
  to?: string | null;
}

export interface NotificationLogQuery extends NotificationLogFilters {
  page: number;
  size: number;
}

/** A message captured by the dev mock SMS provider. */
export interface SmsInboxMessage {
  id: string;
  to: string;
  message: string;
  receivedAt: string;
}

export interface NotificationTemplate {
  id: string;
  eventType: string;
  channel: NotificationChannel;
  subject: string | null;
  /** Plain text with `{{placeholder}}` tokens; never rendered as HTML. */
  body: string;
  active: boolean;
  updatedAt: string;
}

export interface NotificationTemplateUpdate {
  subject: string | null;
  body: string;
  active: boolean;
}
