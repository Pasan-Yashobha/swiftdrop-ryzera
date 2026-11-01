import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  BulkResult,
  CreateOrderRequest,
  Order,
  OrderDetail,
  OrderQuery,
  OrderStats,
} from '../models/order.model';
import { PageResponse } from '../models/page.model';
import { generateId } from '../../shared/util/id';

export interface CreateOrderOptions {
  /** Create a sandbox order instead of a live parcel. */
  sandbox?: boolean;
  /**
   * Reuse the same key when retrying the same submission so the server can de-duplicate it.
   * A fresh key is generated when omitted.
   */
  idempotencyKey?: string;
}

/** Orders of the signed-in merchant (CONTRACT section 5.4, portal endpoints). */
@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/merchants/me/orders`;

  /** A new Idempotency-Key. Generate one per submit attempt and keep it for retries of the same payload. */
  newIdempotencyKey(): string {
    return generateId();
  }

  list(query: OrderQuery = {}): Observable<PageResponse<Order>> {
    return this.http.get<PageResponse<Order>>(this.base, { params: toListParams(query) });
  }

  get(id: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base}/${encodeURIComponent(id)}`);
  }

  /** Counts for live orders only. */
  stats(): Observable<OrderStats> {
    return this.http.get<OrderStats>(`${this.base}/stats`);
  }

  create(request: CreateOrderRequest, options: CreateOrderOptions = {}): Observable<Order> {
    return this.http.post<Order>(this.base, request, {
      params: new HttpParams().set('sandbox', options.sandbox ? 'true' : 'false'),
      headers: { 'Idempotency-Key': options.idempotencyKey ?? this.newIdempotencyKey() },
    });
  }

  /** Uploads a CSV (multipart part `file`). Rows are validated one by one; see BulkResult. */
  uploadBulk(file: File): Observable<BulkResult> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<BulkResult>(`${this.base}/bulk`, body);
  }

  downloadBulkTemplate(): Observable<Blob> {
    return this.http.get(`${this.base}/bulk/template`, { responseType: 'blob' });
  }
}

function toListParams(query: OrderQuery): HttpParams {
  let params = new HttpParams();
  if (query.status) {
    params = params.set('status', query.status);
  }
  if (query.search?.trim()) {
    params = params.set('search', query.search.trim());
  }
  if (query.sandbox === 'sandbox') {
    params = params.set('sandbox', 'true');
  } else if (query.sandbox === 'all') {
    params = params.set('sandbox', 'all');
  }
  if (query.from) {
    params = params.set('from', query.from);
  }
  if (query.to) {
    params = params.set('to', query.to);
  }
  if (query.page !== undefined) {
    params = params.set('page', query.page);
  }
  if (query.size !== undefined) {
    params = params.set('size', query.size);
  }
  return params;
}
