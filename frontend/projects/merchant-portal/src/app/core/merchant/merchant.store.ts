import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { catchError, Observable, of, tap, throwError } from 'rxjs';
import { MerchantApi } from '../api/merchant.api';
import { AuthService } from '../auth/auth.service';
import { AppError, toAppError } from '../http/app-error';
import { Merchant, RegisterMerchantRequest, UpdateMerchantRequest } from '../models/merchant.model';

const NOT_REGISTERED = 'MERCHANT_NOT_REGISTERED';

/** Signal-based state of the signed-in user's merchant (null merchant = not registered yet). */
@Injectable({ providedIn: 'root' })
export class MerchantStore {
  private readonly api = inject(MerchantApi);

  private readonly merchantState = signal<Merchant | null>(null);
  private readonly loadedState = signal(false);
  private readonly loadingState = signal(false);
  private readonly errorState = signal<AppError | null>(null);

  readonly merchant = this.merchantState.asReadonly();
  /** True once the server has been asked at least once for the current user. */
  readonly loaded = this.loadedState.asReadonly();
  readonly loading = this.loadingState.asReadonly();
  readonly error = this.errorState.asReadonly();
  readonly status = computed(() => this.merchantState()?.status ?? null);
  readonly isApproved = computed(() => this.status() === 'APPROVED');

  constructor() {
    const auth = inject(AuthService);
    effect(() => {
      if (!auth.isAuthenticated()) {
        this.reset();
      }
    });
  }

  /** Returns the cached merchant, loading it first if needed. Emits null when none is registered. */
  ensureLoaded(): Observable<Merchant | null> {
    return this.loadedState() ? of(this.merchantState()) : this.refresh();
  }

  /** Always asks the server. Emits null when none is registered; other failures are thrown as AppError. */
  refresh(): Observable<Merchant | null> {
    return this.api.me().pipe(
      catchError((error: unknown) => {
        const appError = toAppError(error);
        return appError.code === NOT_REGISTERED ? of(null) : throwError(() => appError);
      }),
      tap({
        subscribe: () => this.loadingState.set(true),
        next: (merchant) => {
          this.errorState.set(null);
          this.store(merchant);
        },
        error: (error: AppError) => this.errorState.set(error),
        finalize: () => this.loadingState.set(false),
      }),
    );
  }

  register(request: RegisterMerchantRequest): Observable<Merchant> {
    return this.api.register(request).pipe(tap((merchant) => this.store(merchant)));
  }

  update(request: UpdateMerchantRequest): Observable<Merchant> {
    return this.api.update(request).pipe(tap((merchant) => this.store(merchant)));
  }

  resubmit(): Observable<Merchant> {
    return this.api.resubmit().pipe(tap((merchant) => this.store(merchant)));
  }

  /** Forgets what was loaded so the next guard or caller fetches fresh data. */
  invalidate(): void {
    this.loadedState.set(false);
  }

  private reset(): void {
    this.merchantState.set(null);
    this.loadedState.set(false);
    this.loadingState.set(false);
    this.errorState.set(null);
  }

  private store(merchant: Merchant | null): void {
    this.merchantState.set(merchant);
    this.loadedState.set(true);
  }
}
