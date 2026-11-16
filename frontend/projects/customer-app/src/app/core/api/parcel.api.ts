import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { PageResponse } from '../models/page.model';
import {
  CreateCustomerParcelRequest,
  CustomerParcelDetail,
  CustomerParcelQuery,
  CustomerParcelSummary,
  ParcelStatusHistoryDto,
} from '../models/parcel.model';
import { generateId } from '../util/id.util';

@Injectable({ providedIn: 'root' })
export class ParcelApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  private get parcelsUrl(): string {
    return `${this.baseUrl}/parcels`;
  }

  /** A new Idempotency-Key. Generate one per submit attempt and keep it for retries of the same payload. */
  newIdempotencyKey(): string {
    return generateId();
  }

  /**
   * Book a customer parcel delivery with an accepted pricing quote.
   * Calls POST /api/v1/parcels
   * Optionally sends Idempotency-Key header to prevent duplicate orders across network retries.
   */
  create(request: CreateCustomerParcelRequest, idempotencyKey?: string): Observable<CustomerParcelDetail> {
    let headers = new HttpHeaders();
    if (idempotencyKey && idempotencyKey.trim()) {
      headers = headers.set('Idempotency-Key', idempotencyKey.trim());
    }
    return this.http.post<CustomerParcelDetail>(this.parcelsUrl, request, { headers });
  }

  /**
   * Fetch customer parcel details by UUID.
   * Calls GET /api/v1/parcels/{id}
   */
  get(id: string): Observable<CustomerParcelDetail> {
    return this.http.get<CustomerParcelDetail>(`${this.parcelsUrl}/${encodeURIComponent(id)}`);
  }

  /**
   * List customer parcel history with filtering and pagination.
   * Calls GET /api/v1/parcels
   */
  list(query: CustomerParcelQuery = {}): Observable<PageResponse<CustomerParcelSummary>> {
    let params = new HttpParams();

    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.search && query.search.trim()) {
      params = params.set('search', query.search.trim());
    }
    if (query.page !== undefined && query.page !== null) {
      params = params.set('page', query.page.toString());
    }
    if (query.size !== undefined && query.size !== null) {
      params = params.set('size', query.size.toString());
    }
    if (query.sort) {
      params = params.set('sort', query.sort);
    }

    return this.http.get<PageResponse<CustomerParcelSummary>>(this.parcelsUrl, { params });
  }

  /**
   * Fetch audit status history for a parcel.
   * Calls GET /api/v1/parcels/{id}/history
   */
  getHistory(id: string): Observable<ParcelStatusHistoryDto[]> {
    return this.http.get<ParcelStatusHistoryDto[]>(
      `${this.parcelsUrl}/${encodeURIComponent(id)}/history`,
    );
  }
}
