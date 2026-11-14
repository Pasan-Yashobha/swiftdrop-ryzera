import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { PageResponse } from '../models/page.model';
import { DeliveryQuery, WebhookDelivery, WebhookDeliveryDetail } from '../models/webhook.model';
import { toHttpParams } from './query-params';

/** Admin endpoints of the Webhook Delivery Service (member 9). */
@Injectable({ providedIn: 'root' })
export class WebhookAdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/webhooks/deliveries`;

  list(query: DeliveryQuery): Observable<PageResponse<WebhookDelivery>> {
    return this.http.get<PageResponse<WebhookDelivery>>(this.base, {
      params: toHttpParams(query),
    });
  }

  get(id: string): Observable<WebhookDeliveryDetail> {
    return this.http.get<WebhookDeliveryDetail>(`${this.base}/${encodeURIComponent(id)}`);
  }

  /** Queues a FAILED delivery again (202 with the updated row). 409 when it cannot be replayed. */
  replay(id: string): Observable<WebhookDelivery> {
    return this.http.post<WebhookDelivery>(`${this.base}/${encodeURIComponent(id)}/replay`, null);
  }
}
