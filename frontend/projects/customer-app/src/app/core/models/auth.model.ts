export type UserRole = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'ADMIN' | 'DISPATCHER';

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  phone?: string | null;
  avatarUrl?: string | null;
  role: UserRole;
}

export interface AuthSession {
  accessToken: string;
  refreshToken?: string | null;
  expiresIn: number;
  user: AuthUser;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  fullName: string;
  email: string;
  phone?: string | null;
  password: string;
  role?: string;
}

export interface RefreshRequest {
  refreshToken?: string | null;
}
