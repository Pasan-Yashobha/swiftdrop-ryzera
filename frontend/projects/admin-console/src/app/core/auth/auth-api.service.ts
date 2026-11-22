import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { AuthSession, DevTokenResponse, LoginRequest } from '../models/auth.model';

/** Adapter for the Identity service; same response shape as the merchant portal's adapter. */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  login(request: LoginRequest): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.base}/auth/login`, request);
  }

  refresh(refreshToken: string): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.base}/auth/refresh`, { refreshToken });
  }

  /**
   * Development-only sign-in through the dev token endpoint, so the console can be used before Identity
   * issues ADMIN accounts. The session it returns cannot be refreshed.
   */
  devLogin(subject: string): Observable<AuthSession> {
    return this.http
      .post<DevTokenResponse>(`${this.base}/dev/tokens`, { sub: subject, role: 'ADMIN' })
      .pipe(
        map((token) => ({
          accessToken: token.accessToken,
          refreshToken: null,
          expiresIn: token.expiresIn,
          user: {
            id: subject,
            email: '',
            fullName: `Dev admin (${subject})`,
            role: 'ADMIN' as const,
          },
        })),
      );
  }

  /** True for calls that must not carry a bearer token or trigger a token refresh. */
  isAuthUrl(url: string): boolean {
    return url.startsWith(`${this.base}/auth/`) || url === `${this.base}/dev/tokens`;
  }
}
