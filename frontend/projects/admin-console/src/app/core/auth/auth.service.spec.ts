import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { errorInterceptor } from '../http/error.interceptor';
import { aSession } from '../testing/test-data';
import { AuthService } from './auth.service';
import { SESSION_STORAGE_KEY } from './stored-session';

describe('AuthService', () => {
  let http: HttpTestingController;
  let router: Router;

  function setUp(): AuthService {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('starts signed out when nothing is stored', () => {
    const auth = setUp();

    expect(auth.isAuthenticated()).toBeFalse();
    expect(auth.isAdmin()).toBeFalse();
    expect(auth.token()).toBeNull();
  });

  it('signs in with POST /auth/login and keeps the session in sessionStorage only', () => {
    const auth = setUp();

    auth.login({ email: 'ops@swiftdrop.example', password: 'Secret123' }).subscribe();
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ops@swiftdrop.example', password: 'Secret123' });
    request.flush(aSession());

    expect(auth.isAdmin()).toBeTrue();
    expect(auth.token()).toBe('access-1');
    expect(auth.user()?.fullName).toBe('Kasun Silva');
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '{}').accessToken).toBe(
      'access-1',
    );
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('knows a non-admin user is not an admin', () => {
    const auth = setUp();

    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession({}, 'MERCHANT'));

    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.isAdmin()).toBeFalse();
  });

  it('restores a stored session on startup and ignores a corrupt one', () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ accessToken: 'kept', refreshToken: null, user: aSession().user }),
    );
    expect(setUp().token()).toBe('kept');

    TestBed.resetTestingModule();
    sessionStorage.setItem(SESSION_STORAGE_KEY, '{not json');
    expect(setUp().isAuthenticated()).toBeFalse();
  });

  it('dev login asks the dev token endpoint for an ADMIN token', () => {
    const auth = setUp();

    auth.devLogin('admin-1').subscribe();
    const request = http.expectOne('/api/v1/dev/tokens');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ sub: 'admin-1', role: 'ADMIN' });
    request.flush({ accessToken: 'dev-token', expiresIn: 3600 });

    expect(auth.token()).toBe('dev-token');
    expect(auth.user()?.id).toBe('admin-1');
    expect(auth.isAdmin()).toBeTrue();
  });

  it('logout clears the session and goes to the login page', () => {
    const auth = setUp();
    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());

    auth.logout();

    expect(auth.isAuthenticated()).toBeFalse();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], undefined);
  });

  it('refresh swaps in new tokens', () => {
    const auth = setUp();
    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());
    let token: string | null = 'unset';

    auth.refresh().subscribe((value) => (token = value));
    const request = http.expectOne('/api/v1/auth/refresh');
    expect(request.request.body).toEqual({ refreshToken: 'refresh-1' });
    request.flush(aSession({ accessToken: 'access-2' }));

    expect(token).toBe('access-2');
    expect(auth.token()).toBe('access-2');
  });
});
