import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { EstimateRequest, ItemPage, Quote, Zone } from '../models/pricing.model';

/**
 * Pricing & Zones service (Member 5). The bearer token is added by the auth interceptor.
 * Customers and merchants estimate here; Admin manages zones, rate cards and promo codes in the
 * admin console.
 */
@Injectable({ providedIn: 'root' })
export class PricingApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);

  /** Active zones for the estimator's origin and destination pickers. */
  activeZones(): Observable<ItemPage<Zone>> {
    return this.http.get<ItemPage<Zone>>(`${this.api}/pricing/zones`);
  }

  /** Calculates and stores an itemised quote. A promo code is checked but not redeemed. */
  estimate(request: EstimateRequest): Observable<Quote> {
    const body: EstimateRequest = { ...request };
    if (!body.promoCode?.trim()) {
      delete body.promoCode;
    } else {
      body.promoCode = body.promoCode.trim();
    }
    return this.http.post<Quote>(`${this.api}/pricing/estimate`, body);
  }
}
