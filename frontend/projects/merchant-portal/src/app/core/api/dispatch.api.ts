import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  Assignment,
  AutoAssignRequest,
  AutoAssignResponse,
  CourierWorkload,
  ManualAssignRequest,
  ReassignRequest,
  UnassignedParcel,
} from '../models/dispatch.model';

@Injectable({ providedIn: 'root' })
export class DispatchApi {
  private readonly http = inject(HttpClient);
  // Default to gateway path or direct port 8084 fallback
  private readonly baseUrl = `${inject(API_BASE_URL, { optional: true }) ?? 'http://localhost:8084/api/v1'}/dispatch`;

  getUnassignedParcels(zone?: string, size?: string, page = 0, limit = 50): Observable<UnassignedParcel[]> {
    let params = new HttpParams().set('page', page).set('limit', limit);
    if (zone && zone.trim()) {
      params = params.set('zone', zone.trim());
    }
    if (size && size.trim()) {
      params = params.set('size', size.trim());
    }
    return this.http.get<UnassignedParcel[]>(`${this.baseUrl}/parcels/unassigned`, { params });
  }

  getCourierWorkloads(): Observable<CourierWorkload[]> {
    return this.http.get<CourierWorkload[]>(`${this.baseUrl}/couriers/workload`);
  }

  assignManual(parcelId: string, courierId: string, idempotencyKey?: string): Observable<Assignment> {
    let headers = new HttpHeaders();
    if (idempotencyKey) {
      headers = headers.set('Idempotency-Key', idempotencyKey);
    }
    return this.http.post<Assignment>(
      `${this.baseUrl}/assignments`,
      { parcelId, courierId } as ManualAssignRequest,
      { headers }
    );
  }

  autoAssign(parcelIds: string[], strategy: 'LEAST_LOADED' | 'ZONE_BASED', idempotencyKey?: string): Observable<AutoAssignResponse> {
    let headers = new HttpHeaders();
    if (idempotencyKey) {
      headers = headers.set('Idempotency-Key', idempotencyKey);
    }
    return this.http.post<AutoAssignResponse>(
      `${this.baseUrl}/assignments/auto`,
      { parcelIds, strategy } as AutoAssignRequest,
      { headers }
    );
  }

  reassign(parcelId: string, newCourierId: string, reason: string): Observable<Assignment> {
    return this.http.post<Assignment>(
      `${this.baseUrl}/assignments/${parcelId}/reassign`,
      { newCourierId, reason } as ReassignRequest
    );
  }

  getAssignments(parcelId?: string, courierId?: string): Observable<Assignment[]> {
    let params = new HttpParams();
    if (parcelId) {
      params = params.set('parcelId', parcelId);
    }
    if (courierId) {
      params = params.set('courierId', courierId);
    }
    return this.http.get<Assignment[]>(`${this.baseUrl}/assignments`, { params });
  }

  createStream(): EventSource {
    return new EventSource(`${this.baseUrl}/stream`);
  }
}

