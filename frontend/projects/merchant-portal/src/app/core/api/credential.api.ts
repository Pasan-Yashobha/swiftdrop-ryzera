import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/app-config';
import {
  ApiKey,
  ApiKeyCreated,
  CreateApiKeyRequest,
  CreateOAuthClientRequest,
  OAuthClient,
  OAuthClientCreated,
  RotatedClientSecret,
  RotateApiKeyRequest,
} from '../models/credential.model';

/** API keys and OAuth clients of the signed-in merchant (CONTRACT section 5.3). */
@Injectable({ providedIn: 'root' })
export class CredentialApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/merchants/me`;

  listApiKeys(): Observable<ApiKey[]> {
    return this.http.get<ApiKey[]>(`${this.base}/api-keys`);
  }

  /** The response carries the full key exactly once: show it in the secret dialog and drop it. */
  createApiKey(request: CreateApiKeyRequest): Observable<ApiKeyCreated> {
    return this.http.post<ApiKeyCreated>(`${this.base}/api-keys`, request);
  }

  /** Issues a replacement key; the old one keeps working for `graceHours` (0 revokes it immediately). */
  rotateApiKey(id: string, request: RotateApiKeyRequest): Observable<ApiKeyCreated> {
    return this.http.post<ApiKeyCreated>(
      `${this.base}/api-keys/${encodeURIComponent(id)}/rotate`,
      request,
    );
  }

  revokeApiKey(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api-keys/${encodeURIComponent(id)}`);
  }

  listOAuthClients(): Observable<OAuthClient[]> {
    return this.http.get<OAuthClient[]>(`${this.base}/oauth-clients`);
  }

  /** The response carries the client secret exactly once. */
  createOAuthClient(request: CreateOAuthClientRequest): Observable<OAuthClientCreated> {
    return this.http.post<OAuthClientCreated>(`${this.base}/oauth-clients`, request);
  }

  /** The previous secret stops working immediately. */
  rotateOAuthClientSecret(id: string): Observable<RotatedClientSecret> {
    return this.http.post<RotatedClientSecret>(
      `${this.base}/oauth-clients/${encodeURIComponent(id)}/rotate-secret`,
      {},
    );
  }

  revokeOAuthClient(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/oauth-clients/${encodeURIComponent(id)}`);
  }
}
