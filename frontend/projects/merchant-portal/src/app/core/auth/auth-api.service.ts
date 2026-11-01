import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { AuthSession, DevTokenResponse, LoginRequest, SignupRequest } from '../models/auth.model';

/**
 * Adapter for the Identity service (member 2). Its endpoints are ASSUMED in CONTRACT section 10, so
 * everything that depends on their shape lives in this one class.
 */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  login(request: LoginRequest): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.base}/auth/login`, request);
  }

  signup(request: SignupRequest): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.base}/auth/signup`, request);
  }

  refresh(refreshToken: string): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.base}/auth/refresh`, { refreshToken });
  }

  /**
   * Development-only sign-in against merchant-service's dev token endpoint, for working on the portal
   * before Identity exists. The session it returns cannot be refreshed.
   */
  devLogin(subject: string): Observable<AuthSession> {
    return this.http
      .post<DevTokenResponse>(`${this.base}/dev/tokens`, { sub: subject, role: 'MERCHANT' })
      .pipe(
        map((token) => ({
          accessToken: token.accessToken,
          refreshToken: null,
          expiresIn: token.expiresIn,
          user: {
            id: subject,
            email: '',
            fullName: `Dev merchant (${subject})`,
            role: 'MERCHANT' as const,
          },
        })),
      );
  }

  /** True for calls that must not carry a bearer token or trigger a token refresh. */
  isAuthUrl(url: string): boolean {
    return url.startsWith(`${this.base}/auth/`) || url === `${this.base}/dev/tokens`;
  }
}
