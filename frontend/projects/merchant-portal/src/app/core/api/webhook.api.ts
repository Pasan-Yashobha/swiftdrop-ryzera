import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  SaveWebhookRequest,
  SavedWebhook,
  WebhookConfig,
  WebhookDeliveryPage,
  WebhookSecret,
  WebhookTestResult,
} from '../models/webhook.model';

/** Webhook configuration and delivery log of the signed-in merchant (CONTRACT section 5.5). */
@Injectable({ providedIn: 'root' })
export class WebhookApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/merchants/me/webhook`;

  get(): Observable<WebhookConfig> {
    return this.http.get<WebhookConfig>(this.base);
  }

  /** Creates or updates the configuration. The response includes `secret` only when it was just created. */
  save(request: SaveWebhookRequest): Observable<SavedWebhook> {
    return this.http.put<SavedWebhook>(this.base, request);
  }

  /** Generates a new signing secret; shown once in the response. */
  rotateSecret(): Observable<WebhookSecret> {
    return this.http.post<WebhookSecret>(`${this.base}/rotate-secret`, {});
  }

  /** Sends a signed `webhook.test` event to the configured URL. */
  test(): Observable<WebhookTestResult> {
    return this.http.post<WebhookTestResult>(`${this.base}/test`, {});
  }

  remove(): Observable<void> {
    return this.http.delete<void>(this.base);
  }

  deliveries(page = 0, size = 20): Observable<WebhookDeliveryPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<WebhookDeliveryPage>(`${this.base}/deliveries`, { params });
  }
}
