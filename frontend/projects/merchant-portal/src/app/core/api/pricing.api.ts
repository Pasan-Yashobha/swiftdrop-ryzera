import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  EstimateRequest,
  ItemPage,
  PromoCode,
  PromoCodeInput,
  Quote,
  RateCard,
  RateCardInput,
  Zone,
  ZoneInput,
} from '../models/pricing.model';

/** Admin collections of the Pricing service. Each supports list, get, create, update and soft-delete. */
export type PricingCollection = 'zones' | 'rate-cards' | 'promo-codes';

/**
 * Pricing & Zones service (Member 5). The bearer token is added by the auth interceptor.
 * Customers and merchants estimate; only Admin manages zones, rate cards and promo codes.
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

  listZones(page = 0, size = 100): Observable<ItemPage<Zone>> {
    return this.list<Zone>('zones', page, size);
  }

  saveZone(input: ZoneInput, id?: string): Observable<Zone> {
    return this.save<Zone>('zones', input, id);
  }

  listRateCards(page = 0, size = 100): Observable<ItemPage<RateCard>> {
    return this.list<RateCard>('rate-cards', page, size);
  }

  saveRateCard(input: RateCardInput, id?: string): Observable<RateCard> {
    return this.save<RateCard>('rate-cards', input, id);
  }

  listPromoCodes(page = 0, size = 100): Observable<ItemPage<PromoCode>> {
    return this.list<PromoCode>('promo-codes', page, size);
  }

  savePromoCode(input: PromoCodeInput, id?: string): Observable<PromoCode> {
    return this.save<PromoCode>('promo-codes', input, id);
  }

  /** Soft delete: the record stays for history but is no longer offered. */
  deactivate(collection: PricingCollection, id: string): Observable<void> {
    return this.http.delete<void>(this.adminUrl(collection, id));
  }

  private list<T>(
    collection: PricingCollection,
    page: number,
    size: number,
  ): Observable<ItemPage<T>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ItemPage<T>>(this.adminUrl(collection), { params });
  }

  private save<T>(collection: PricingCollection, body: unknown, id?: string): Observable<T> {
    return id
      ? this.http.put<T>(this.adminUrl(collection, id), body)
      : this.http.post<T>(this.adminUrl(collection), body);
  }

  private adminUrl(collection: PricingCollection, id?: string): string {
    const base = `${this.api}/admin/${collection}`;
    return id ? `${base}/${encodeURIComponent(id)}` : base;
  }
}
