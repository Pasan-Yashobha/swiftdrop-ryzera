import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, map, Observable, of, share, throwError } from 'rxjs';
import { isAppError } from '../http/app-error';
import { AuthSession, AuthUser, LoginRequest } from '../models/auth.model';
import { AuthApiService } from './auth-api.service';
import {
  clearStoredSession,
  readStoredSession,
  StoredSession,
  writeStoredSession,
} from './stored-session';

export interface LogoutOptions {
  /** Set when the session ended because the server stopped accepting it, so the login page can say so. */
  expired?: boolean;
}

/**
 * Holds the signed-in user and tokens (signals, mirrored to sessionStorage) and owns login, refresh and
 * logout. Tokens are never logged.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApiService);
  private readonly router = inject(Router);

  private readonly session = signal<StoredSession | null>(readStoredSession());
  private refreshInFlight: Observable<string | null> | null = null;

  readonly user = computed<AuthUser | null>(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.accessToken ?? null);
  readonly role = computed(() => this.user()?.role ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');

  login(credentials: LoginRequest): Observable<AuthUser> {
    return this.api.login(credentials).pipe(map((session) => this.startSession(session)));
  }

  devLogin(subject: string): Observable<AuthUser> {
    return this.api.devLogin(subject).pipe(map((session) => this.startSession(session)));
  }

  /**
   * Exchanges the refresh token for a new access token. Concurrent callers share one request.
   * Emits null when the session cannot be renewed (no refresh token, or Identity rejected it);
   * transient failures (network, 5xx) are rethrown so a blip does not sign the admin out.
   */
  refresh(): Observable<string | null> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    const refreshToken = this.session()?.refreshToken;
    if (!refreshToken) {
      return of(null);
    }
    this.refreshInFlight = this.api.refresh(refreshToken).pipe(
      map((session) => {
        this.startSession(session);
        return session.accessToken;
      }),
      catchError((error: unknown) =>
        isAppError(error) && (error.status === 0 || error.isServerError)
          ? throwError(() => error)
          : of(null),
      ),
      finalize(() => (this.refreshInFlight = null)),
      share(),
    );
    return this.refreshInFlight;
  }

  logout(options: LogoutOptions = {}): void {
    const returnUrl = this.router.url;
    this.session.set(null);
    clearStoredSession();
    void this.router.navigate(
      ['/login'],
      options.expired ? { queryParams: { reason: 'expired', returnUrl } } : undefined,
    );
  }

  private startSession(session: AuthSession): AuthUser {
    const stored: StoredSession = {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken ?? null,
      user: session.user,
    };
    this.session.set(stored);
    writeStoredSession(stored);
    return session.user;
  }
}
