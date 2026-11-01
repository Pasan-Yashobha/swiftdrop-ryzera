export type CredentialEnvironment = 'LIVE' | 'TEST';
export type CredentialStatus = 'ACTIVE' | 'REVOKED';
export type OAuthScope = 'orders:write' | 'orders:read';

export const OAUTH_SCOPES: readonly OAuthScope[] = ['orders:write', 'orders:read'];

/** A key as listed: never contains the secret. */
export interface ApiKey {
  id: string;
  label: string;
  environment: CredentialEnvironment;
  keyPrefix: string;
  status: CredentialStatus;
  expiresAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
  revokedAt: string | null;
}

/** Returned once on create / rotate. `apiKey` is the full key and is never available again. */
export interface ApiKeyCreated extends ApiKey {
  apiKey: string;
}

export interface CreateApiKeyRequest {
  label: string;
  environment: CredentialEnvironment;
}

export interface RotateApiKeyRequest {
  /** 0 revokes the old key immediately; allowed range 0..72, default 24. */
  graceHours: number;
}

export interface OAuthClient {
  id: string;
  clientId: string;
  label: string;
  environment: CredentialEnvironment;
  scopes: OAuthScope[];
  status: CredentialStatus;
  lastUsedAt: string | null;
  createdAt: string;
  revokedAt: string | null;
}

/** Returned once on create. `clientSecret` is never available again. */
export interface OAuthClientCreated extends OAuthClient {
  clientSecret: string;
}

export interface CreateOAuthClientRequest {
  label: string;
  environment: CredentialEnvironment;
  scopes: OAuthScope[];
}

/** Response of a secret rotation: the new secret, shown once. */
export interface RotatedClientSecret {
  clientSecret: string;
}
