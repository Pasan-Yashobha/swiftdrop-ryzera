import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSession, LoginRequest, SignupRequest } from '../models/auth.model';
import { AuthApi } from './auth.api';

describe('AuthApi', () => {
  let api: AuthApi;
  let http: HttpTestingController;

  const mockSession: AuthSession = {
    accessToken: 'test-access-token',
    refreshToken: 'test-refresh-token',
    expiresIn: 900,
    user: {
      id: 'e0a80101-7788-44aa-99bb-112233445566',
      email: 'customer@swiftdrop.lk',
      fullName: 'Sithu Perera',
      phone: '+94771234567',
      avatarUrl: null,
      role: 'CUSTOMER',
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuthApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('calls POST /api/v1/auth/signup with withCredentials: true', () => {
    const signupReq: SignupRequest = {
      fullName: 'Sithu Perera',
      email: 'customer@swiftdrop.lk',
      phone: '+94771234567',
      password: 'SecurePassword123!',
      role: 'CUSTOMER',
    };

    api.signup(signupReq).subscribe((session) => {
      expect(session).toEqual(mockSession);
      expect(session.user.role).toBe('CUSTOMER');
    });

    const req = http.expectOne('/api/v1/auth/signup');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual(signupReq);
    req.flush(mockSession);
  });

  it('calls POST /api/v1/auth/login with withCredentials: true', () => {
    const loginReq: LoginRequest = {
      email: 'customer@swiftdrop.lk',
      password: 'SecurePassword123!',
    };

    api.login(loginReq).subscribe((session) => {
      expect(session.accessToken).toBe('test-access-token');
    });

    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual(loginReq);
    req.flush(mockSession);
  });

  it('calls POST /api/v1/auth/refresh with optional refresh token body', () => {
    api.refresh('custom-refresh-token').subscribe();

    const req = http.expectOne('/api/v1/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({ refreshToken: 'custom-refresh-token' });
    req.flush(mockSession);
  });

  it('calls POST /api/v1/auth/refresh with empty body when relying on HttpOnly cookie', () => {
    api.refresh().subscribe();

    const req = http.expectOne('/api/v1/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({});
    req.flush(mockSession);
  });

  it('calls POST /api/v1/auth/logout with withCredentials: true', () => {
    api.logout().subscribe();

    const req = http.expectOne('/api/v1/auth/logout');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush(null);
  });

  it('identifies auth URLs correctly', () => {
    expect(api.isAuthUrl('/api/v1/auth/login')).toBeTrue();
    expect(api.isAuthUrl('/api/v1/auth/signup')).toBeTrue();
    expect(api.isAuthUrl('/api/v1/parcels')).toBeFalse();
    expect(api.isAuthUrl('/api/v1/tracking/SD260001001')).toBeFalse();
  });
});
