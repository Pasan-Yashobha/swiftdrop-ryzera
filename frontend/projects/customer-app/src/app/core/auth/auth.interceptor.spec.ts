import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let authApiSpy: jasmine.SpyObj<AuthApi>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['token', 'refresh', 'logout']);
    authApiSpy = jasmine.createSpyObj('AuthApi', ['isAuthUrl']);

    // Default mock behavior
    authApiSpy.isAuthUrl.and.callFake((url: string) => url.includes('/api/v1/auth'));
    authServiceSpy.token.and.returnValue('initial-jwt-token');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: AuthApi, useValue: authApiSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('attaches Authorization Bearer header to protected requests', () => {
    http.get('/api/v1/parcels').subscribe();

    const req = httpMock.expectOne('/api/v1/parcels');
    expect(req.request.headers.get('Authorization')).toBe('Bearer initial-jwt-token');
    req.flush([]);
  });

  it('does not attach Authorization header to public tracking endpoints', () => {
    http.get('/api/v1/tracking/SD260001001').subscribe();

    const req = httpMock.expectOne('/api/v1/tracking/SD260001001');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not attach Authorization header to auth endpoints', () => {
    http.post('/api/v1/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('refreshes token and retries request on 401 Unauthorized', () => {
    authServiceSpy.refresh.and.returnValue(of('fresh-jwt-token'));

    http.get('/api/v1/parcels').subscribe((data) => {
      expect(data).toEqual([{ id: '123' }]);
    });

    // Initial request fails with 401
    const req1 = httpMock.expectOne('/api/v1/parcels');
    req1.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refresh).toHaveBeenCalled();

    // Retried request with new token and retry header
    const req2 = httpMock.expectOne('/api/v1/parcels');
    expect(req2.request.headers.get('Authorization')).toBe('Bearer fresh-jwt-token');
    expect(req2.request.headers.get('X-Auth-Retry')).toBe('1');
    req2.flush([{ id: '123' }]);
  });

  it('does not retry infinitely if the retried request fails with 401', () => {
    authServiceSpy.refresh.and.returnValue(of('fresh-jwt-token'));

    let errorReceived: HttpErrorResponse | null = null;
    http.get('/api/v1/parcels').subscribe({
      error: (err) => {
        errorReceived = err;
      },
    });

    // Initial request fails with 401
    const req1 = httpMock.expectOne('/api/v1/parcels');
    req1.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    // Retried request also fails with 401
    const req2 = httpMock.expectOne('/api/v1/parcels');
    req2.flush({ message: 'Still unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(errorReceived).not.toBeNull();
    // Refresh should only have been called once, not in an infinite loop
    expect(authServiceSpy.refresh).toHaveBeenCalledTimes(1);
  });

  it('calls auth.logout({ expired: true }) if refresh fails', () => {
    authServiceSpy.refresh.and.returnValue(throwError(() => new Error('Refresh token revoked')));

    let errorReceived: unknown = null;
    http.get('/api/v1/parcels').subscribe({
      error: (err) => {
        errorReceived = err;
      },
    });

    const req = httpMock.expectOne('/api/v1/parcels');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalledWith({ expired: true });
    expect(errorReceived).not.toBeNull();
  });
});
