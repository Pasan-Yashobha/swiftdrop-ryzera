import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { Merchant, RegisterMerchantRequest, UpdateMerchantRequest } from '../models/merchant.model';

/** Merchant profile endpoints (CONTRACT section 5.1). */
@Injectable({ providedIn: 'root' })
export class MerchantApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/merchants`;

  register(request: RegisterMerchantRequest): Observable<Merchant> {
    return this.http.post<Merchant>(`${this.base}/register`, request);
  }

  /** Fails with 404 MERCHANT_NOT_REGISTERED when the signed-in user has not registered a business yet. */
  me(): Observable<Merchant> {
    return this.http.get<Merchant>(`${this.base}/me`);
  }

  update(request: UpdateMerchantRequest): Observable<Merchant> {
    return this.http.put<Merchant>(`${this.base}/me`, request);
  }

  /** Moves a REJECTED merchant back to PENDING. */
  resubmit(): Observable<Merchant> {
    return this.http.post<Merchant>(`${this.base}/me/resubmit`, {});
  }
}
