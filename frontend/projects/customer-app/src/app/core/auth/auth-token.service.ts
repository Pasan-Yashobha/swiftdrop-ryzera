import { Injectable, signal } from '@angular/core';

const TOKEN_STORAGE_KEY = 'swiftdrop_customer_token';

/**
 * Lightweight token state holder preparing Customer App for JWT authentication.
 * Does not invent mock login endpoints or authentication flows; stores and supplies
 * the bearer token for outgoing authenticated Parcel Service requests.
 */
@Injectable({ providedIn: 'root' })
export class AuthTokenService {
  private readonly _token = signal<string | null>(this.loadStoredToken());

  readonly token = this._token.asReadonly();

  hasToken(): boolean {
    return !!this._token();
  }

  setToken(token: string | null): void {
    this._token.set(token);
    try {
      if (token) {
        localStorage.setItem(TOKEN_STORAGE_KEY, token);
      } else {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
      }
    } catch {
      // Ignore storage errors in restricted contexts
    }
  }

  clearToken(): void {
    this.setToken(null);
  }

  private loadStoredToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
