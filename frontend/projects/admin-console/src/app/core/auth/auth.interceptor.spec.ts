import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AppError } from '../http/app-error';
import { errorInterceptor } from '../http/error.interceptor';
import { aSession } from '../testing/test-data';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

const LOGS_URL = '/api/v1/admin/notifications';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function signIn(): void {
    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());
  }

  it('adds the bearer token to API calls', () => {
    signIn();

    client.get(LOGS_URL).subscribe();

    expect(http.expectOne(LOGS_URL).request.headers.get('Authorization')).toBe('Bearer access-1');
  });

  it('does not add the token to login or dev token calls, or to other origins', () => {
    signIn();

    client.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    client.post('/api/v1/dev/tokens', {}).subscribe({ error: () => undefined });
    client.get('https://cdn.example/lib.js').subscribe();

    expect(http.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBeFalse();
    expect(http.expectOne('/api/v1/dev/tokens').request.headers.has('Authorization')).toBeFalse();
    expect(
      http.expectOne('https://cdn.example/lib.js').request.headers.has('Authorization'),
    ).toBeFalse();
  });

  it('sends the admin back to the login page on 401 when the session cannot be refreshed', () => {
    auth.devLogin('admin-1').subscribe();
    http.expectOne('/api/v1/dev/tokens').flush({ accessToken: 'dev', expiresIn: 3600 });
    let failure: unknown;

    client.get(LOGS_URL).subscribe({ error: (error) => (failure = error) });
    http.expectOne(LOGS_URL).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failure).toBeInstanceOf(AppError);
    expect(auth.isAuthenticated()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'],
      jasmine.objectContaining({ queryParams: jasmine.objectContaining({ reason: 'expired' }) }),
    );
  });

  it('refreshes once on 401 and replays the request with the new token', () => {
    signIn();
    let body: unknown;

    client.get(LOGS_URL).subscribe((value) => (body = value));
    http.expectOne(LOGS_URL).flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush(aSession({ accessToken: 'access-2' }));
    const replay = http.expectOne(LOGS_URL);
    expect(replay.request.headers.get('Authorization')).toBe('Bearer access-2');
    replay.flush({ ok: true });

    expect(body).toEqual({ ok: true });
  });

  it('passes other errors through as AppError with the server message', () => {
    signIn();
    let failure: AppError | undefined;

    client.post(`${LOGS_URL}/n-1/resend`, null).subscribe({ error: (error) => (failure = error) });
    http
      .expectOne(`${LOGS_URL}/n-1/resend`)
      .flush(
        { status: 409, code: 'INVALID_STATE', message: 'Only FAILED notifications can be resent', correlationId: 'c-1' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(failure?.code).toBe('INVALID_STATE');
    expect(failure?.message).toBe('Only FAILED notifications can be resent');
    expect(failure?.correlationId).toBe('c-1');
    expect(auth.isAuthenticated()).toBeTrue();
  });
});
