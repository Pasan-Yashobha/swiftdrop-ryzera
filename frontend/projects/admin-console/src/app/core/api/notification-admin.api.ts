import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  NotificationLog,
  NotificationLogQuery,
  NotificationTemplate,
  NotificationTemplateUpdate,
  SmsInboxMessage,
} from '../models/notification.model';
import { PageResponse } from '../models/page.model';
import { toHttpParams } from './query-params';

/** Admin endpoints of the Notification Service (member 9). */
@Injectable({ providedIn: 'root' })
export class NotificationAdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/notifications`;

  list(query: NotificationLogQuery): Observable<PageResponse<NotificationLog>> {
    return this.http.get<PageResponse<NotificationLog>>(this.base, {
      params: toHttpParams(query),
    });
  }

  /** Sends a FAILED notification again. 409 when it is not in a resendable state. */
  resend(id: string): Observable<NotificationLog> {
    return this.http.post<NotificationLog>(`${this.base}/${encodeURIComponent(id)}/resend`, null);
  }

  smsInbox(page: number, size: number): Observable<PageResponse<SmsInboxMessage>> {
    return this.http.get<PageResponse<SmsInboxMessage>>(`${this.base}/sms-inbox`, {
      params: toHttpParams({ page, size }),
    });
  }

  templates(): Observable<NotificationTemplate[]> {
    return this.http.get<NotificationTemplate[]>(`${this.base}/templates`);
  }

  updateTemplate(id: string, update: NotificationTemplateUpdate): Observable<NotificationTemplate> {
    return this.http.put<NotificationTemplate>(
      `${this.base}/templates/${encodeURIComponent(id)}`,
      update,
    );
  }
}
