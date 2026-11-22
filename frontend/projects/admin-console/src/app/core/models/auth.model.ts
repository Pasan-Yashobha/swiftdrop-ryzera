export type UserRole = 'MERCHANT' | 'ADMIN' | 'DISPATCHER' | 'CUSTOMER' | 'COURIER';

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/** Identity's login / refresh response. */
export interface AuthSession {
  accessToken: string;
  /** Null for dev-login sessions, which cannot be refreshed. */
  refreshToken: string | null;
  expiresIn: number;
  user: AuthUser;
}

export interface DevTokenResponse {
  accessToken: string;
  expiresIn: number;
}
