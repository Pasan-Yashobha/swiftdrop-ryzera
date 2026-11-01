import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { errorInterceptor } from '../http/error.interceptor';
import { aSession } from '../testing/test-data';
import { AuthService } from './auth.service';

const STORAGE_KEY = 'swiftdrop.merchant-portal.session';

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
    expect(auth.user()).toBeNull();
    expect(auth.token()).toBeNull();
    expect(auth.role()).toBeNull();
  });

  it('signs in, exposes the session as signals and keeps it in sessionStorage only', () => {
    const auth = setUp();

    auth.login({ email: 'owner@lankacrafts.example', password: 'Secret123' }).subscribe();
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'owner@lankacrafts.example',
      password: 'Secret123',
    });
    request.flush(aSession());

    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.isMerchant()).toBeTrue();
    expect(auth.token()).toBe('access-1');
    expect(auth.user()?.fullName).toBe('Nimali Perera');
    expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '{}').accessToken).toBe('access-1');
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('restores a stored session on startup', () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ accessToken: 'kept', refreshToken: null, user: aSession().user }),
    );

    const auth = setUp();

    expect(auth.token()).toBe('kept');
    expect(auth.role()).toBe('MERCHANT');
  });

  it('ignores a corrupt stored session', () => {
    sessionStorage.setItem(STORAGE_KEY, '{not json');

    expect(setUp().isAuthenticated()).toBeFalse();
  });

  it('ignores a stored session with an unknown role', () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        accessToken: 'x',
        refreshToken: null,
        user: { ...aSession().user, role: 'ROOT' },
      }),
    );

    expect(setUp().isAuthenticated()).toBeFalse();
  });

  it('keeps working in memory when sessionStorage throws', () => {
    const auth = setUp();
    spyOn(Storage.prototype, 'setItem').and.throwError('quota');

    auth
      .signup({
        fullName: 'N P',
        email: 'a@b.lk',
        phone: '0771234567',
        password: 'Secret123',
        role: 'MERCHANT',
      })
      .subscribe();
    http.expectOne('/api/v1/auth/signup').flush(aSession());

    expect(auth.isAuthenticated()).toBeTrue();
  });

  it('signs in with the dev endpoint and builds a user from the subject', () => {
    const auth = setUp();

    auth.devLogin('user-7').subscribe();
    const request = http.expectOne('/api/v1/dev/tokens');
    expect(request.request.body).toEqual({ sub: 'user-7', role: 'MERCHANT' });
    request.flush({ accessToken: 'dev-token', expiresIn: 3600 });

    expect(auth.token()).toBe('dev-token');
    expect(auth.user()?.id).toBe('user-7');
    expect(auth.isMerchant()).toBeTrue();
  });

  it('logout clears the session and goes to the login page', () => {
    const auth = setUp();
    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());

    auth.logout();

    expect(auth.isAuthenticated()).toBeFalse();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], undefined);
  });

  it('logout after expiry tells the login page why and where to return', () => {
    const auth = setUp();

    auth.logout({ expired: true });

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { reason: 'expired', returnUrl: router.url },
    });
  });

  describe('refresh', () => {
    function signedIn(session = aSession()): AuthService {
      const auth = setUp();
      auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
      http.expectOne('/api/v1/auth/login').flush(session);
      return auth;
    }

    it('swaps in the new tokens', () => {
      const auth = signedIn();
      let token: string | null = '';

      auth.refresh().subscribe((value) => (token = value));
      const request = http.expectOne('/api/v1/auth/refresh');
      expect(request.request.body).toEqual({ refreshToken: 'refresh-1' });
      request.flush(aSession({ accessToken: 'access-2', refreshToken: 'refresh-2' }));

      expect(token).toBe('access-2');
      expect(auth.token()).toBe('access-2');
    });

    it('shares one request between concurrent callers', () => {
      const auth = signedIn();
      const results: (string | null)[] = [];

      auth.refresh().subscribe((value) => results.push(value));
      auth.refresh().subscribe((value) => results.push(value));
      http.expectOne('/api/v1/auth/refresh').flush(aSession({ accessToken: 'access-2' }));

      expect(results).toEqual(['access-2', 'access-2']);
    });

    it('emits null without calling the server when there is no refresh token', () => {
      const auth = signedIn(aSession({ refreshToken: null }));
      let token: string | null = 'unset';

      auth.refresh().subscribe((value) => (token = value));

      expect(token).toBeNull();
    });

    it('emits null when Identity rejects the refresh token', () => {
      const auth = signedIn();
      let token: string | null = 'unset';

      auth.refresh().subscribe((value) => (token = value));
      http
        .expectOne('/api/v1/auth/refresh')
        .flush(
          { code: 'INVALID_TOKEN', message: 'expired' },
          { status: 401, statusText: 'Unauthorized' },
        );

      expect(token).toBeNull();
    });

    it('rethrows transient failures so the user stays signed in', () => {
      const auth = signedIn();
      let failure: unknown;

      auth.refresh().subscribe({ error: (error) => (failure = error) });
      http
        .expectOne('/api/v1/auth/refresh')
        .flush(null, { status: 503, statusText: 'Service Unavailable' });

      expect(failure).toBeDefined();
      expect(auth.isAuthenticated()).toBeTrue();
    });
  });
});
