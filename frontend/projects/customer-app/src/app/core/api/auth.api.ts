import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import { AuthSession, LoginRequest, RefreshRequest, SignupRequest } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  private get authUrl(): string {
    return `${this.baseUrl}/auth`;
  }

  /**
   * Register a new customer user.
   * Calls POST /api/v1/auth/signup
   */
  signup(request: SignupRequest): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.authUrl}/signup`, request, {
      withCredentials: true,
    });
  }

  /**
   * Authenticate customer with email and password.
   * Calls POST /api/v1/auth/login
   */
  login(request: LoginRequest): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.authUrl}/login`, request, {
      withCredentials: true,
    });
  }

  /**
   * Renew expired access token using the refresh token (body or HttpOnly cookie).
   * Calls POST /api/v1/auth/refresh
   */
  refresh(refreshToken?: string | null): Observable<AuthSession> {
    const body: RefreshRequest = refreshToken ? { refreshToken } : {};
    return this.http.post<AuthSession>(`${this.authUrl}/refresh`, body, {
      withCredentials: true,
    });
  }

  /**
   * Terminate user session and revoke refresh token.
   * Calls POST /api/v1/auth/logout
   */
  logout(refreshToken?: string | null): Observable<void> {
    const body: RefreshRequest = refreshToken ? { refreshToken } : {};
    return this.http.post<void>(`${this.authUrl}/logout`, body, {
      withCredentials: true,
    });
  }

  /**
   * Checks if an outgoing URL belongs to the auth endpoints.
   */
  isAuthUrl(url: string): boolean {
    return url.startsWith(this.authUrl);
  }
}
