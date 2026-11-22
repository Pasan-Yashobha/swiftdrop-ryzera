import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, map, Observable, of, share } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { AuthSession, AuthUser, LoginRequest, SignupRequest } from '../models/auth.model';

export interface LogoutOptions {
  expired?: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);

  // In-memory access token and user profile; never persists refresh tokens in storage
  private readonly _user = signal<AuthUser | null>(null);
  private readonly _token = signal<string | null>(null);
  private readonly _initialized = signal(false);

  private refreshInFlight: Observable<string | null> | null = null;

  readonly user = this._user.asReadonly();
  readonly token = this._token.asReadonly();
  readonly initialized = this._initialized.asReadonly();

  readonly isAuthenticated = computed(() => !!this._token() && !!this._user());
  readonly isCustomer = computed(() => this._user()?.role === 'CUSTOMER');

  /**
   * Helper to check if a valid bearer token is currently held in memory.
   */
  hasToken(): boolean {
    return !!this._token();
  }

  /**
   * Sign in customer with email and password.
   */
  login(credentials: LoginRequest): Observable<AuthUser> {
    return this.api.login(credentials).pipe(
      map((session) => {
        this.setSession(session);
        return session.user;
      }),
    );
  }

  /**
   * Register a new customer user (role is forced to CUSTOMER).
   */
  signup(request: Omit<SignupRequest, 'role'>): Observable<AuthUser> {
    const customerSignup: SignupRequest = {
      ...request,
      role: 'CUSTOMER',
    };
    return this.api.signup(customerSignup).pipe(
      map((session) => {
        this.setSession(session);
        return session.user;
      }),
    );
  }

  /**
   * Safely restore an active session from the server-managed HttpOnly refresh token cookie on app startup.
   */
  hydrateFromCookie(): Observable<boolean> {
    if (this.isAuthenticated()) {
      this._initialized.set(true);
      return of(true);
    }

    return this.api.refresh().pipe(
      map((session) => {
        this.setSession(session);
        this._initialized.set(true);
        return true;
      }),
      catchError(() => {
        this.clearSession();
        this._initialized.set(true);
        return of(false);
      }),
    );
  }

  /**
   * Refreshes the short-lived access token.
   * Deduplicates concurrent calls so multiple parallel 401s trigger only one refresh request.
   */
  refresh(): Observable<string | null> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }

    this.refreshInFlight = this.api.refresh().pipe(
      map((session) => {
        this.setSession(session);
        return session.accessToken;
      }),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
      finalize(() => {
        this.refreshInFlight = null;
      }),
      share(),
    );

    return this.refreshInFlight;
  }

  /**
   * Sign out customer, notify backend to revoke the refresh token, and redirect to login.
   */
  logout(options: LogoutOptions = {}): void {
    const returnUrl = this.router.url;
    this.api.logout().pipe(
      catchError(() => of(undefined)),
      finalize(() => {
        this.clearSession();
        void this.router.navigate(['/login'], {
          queryParams: options.expired ? { reason: 'expired', returnUrl } : undefined,
        });
      }),
    ).subscribe();
  }

  private setSession(session: AuthSession): void {
    this._token.set(session.accessToken);
    this._user.set(session.user);
  }

  private clearSession(): void {
    this._token.set(null);
    this._user.set(null);
  }
}
