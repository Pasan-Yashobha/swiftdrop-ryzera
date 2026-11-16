import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  PricingEstimateRequest,
  PricingEstimateResponse,
  PricingZone,
  PricingZonesResponse,
} from '../models/pricing.model';

@Injectable({ providedIn: 'root' })
export class PricingApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  private get pricingUrl(): string {
    return `${this.baseUrl}/pricing`;
  }

  /**
   * Fetch active delivery zones for origin and destination selection.
   * Calls GET /api/v1/pricing/zones
   */
  getZones(): Observable<PricingZone[]> {
    return this.http
      .get<PricingZonesResponse>(`${this.pricingUrl}/zones`)
      .pipe(map((res) => res.items || []));
  }

  /**
   * Request upfront pricing estimate and quote reservation.
   * Calls POST /api/v1/pricing/estimate
   */
  estimate(request: PricingEstimateRequest): Observable<PricingEstimateResponse> {
    return this.http.post<PricingEstimateResponse>(`${this.pricingUrl}/estimate`, request);
  }
}
