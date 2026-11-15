import { inject, Injectable } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Adapter delegating to AuthService to preserve compatibility.
 */
@Injectable({ providedIn: 'root' })
export class AuthTokenService {
  private readonly auth = inject(AuthService);

  readonly token = this.auth.token;

  hasToken(): boolean {
    return this.auth.hasToken();
  }

  clearToken(): void {
    this.auth.logout();
  }
}
