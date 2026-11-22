import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { PublicTrackingResponse } from '../models/tracking.model';

@Injectable({ providedIn: 'root' })
export class TrackingApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * Track a parcel publicly by its unique tracking number (e.g. SD260001001).
   * Calls GET /api/v1/tracking/{trackingNo}
   */
  track(trackingNumber: string): Observable<PublicTrackingResponse> {
    const cleanNo = encodeURIComponent(trackingNumber.trim().toUpperCase());
    return this.http.get<PublicTrackingResponse>(`${this.baseUrl}/tracking/${cleanNo}`);
  }
}
