import { HttpClient } from '@angular/common/http';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { errorInterceptor } from '../http/error.interceptor';
import { AppError } from '../http/app-error';
import { aSession } from '../testing/test-data';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

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
    auth.login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('adds the bearer token to API calls', () => {
    client.get('/api/v1/merchants/me').subscribe();

    expect(http.expectOne('/api/v1/merchants/me').request.headers.get('Authorization')).toBe(
      'Bearer access-1',
    );
  });

  it('does not add the token to login, signup, refresh or dev token calls', () => {
    client.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    client.post('/api/v1/dev/tokens', {}).subscribe({ error: () => undefined });

    expect(http.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBeFalse();
    expect(http.expectOne('/api/v1/dev/tokens').request.headers.has('Authorization')).toBeFalse();
  });

  it('does not touch requests to other origins or assets', () => {
    client.get('https://cdn.example/lib.js').subscribe();

    expect(
      http.expectOne('https://cdn.example/lib.js').request.headers.has('Authorization'),
    ).toBeFalse();
  });

  it('refreshes once on 401 and replays the request with the new token', () => {
    let body: unknown;
    client.get('/api/v1/merchants/me').subscribe((value) => (body = value));

    http.expectOne('/api/v1/merchants/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne('/api/v1/auth/refresh')
      .flush(aSession({ accessToken: 'access-2', refreshToken: 'refresh-2' }));
    const replay = http.expectOne('/api/v1/merchants/me');
    expect(replay.request.headers.get('Authorization')).toBe('Bearer access-2');
    replay.flush({ ok: true });

    expect(body).toEqual({ ok: true });
    expect(auth.token()).toBe('access-2');
  });

  it('ends the session when the refresh token is rejected', () => {
    let failure: unknown;
    client.get('/api/v1/merchants/me').subscribe({ error: (error) => (failure = error) });

    http.expectOne('/api/v1/merchants/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne('/api/v1/auth/refresh')
      .flush({ code: 'INVALID_TOKEN', message: 'no' }, { status: 401, statusText: 'Unauthorized' });

    expect(failure).toBeInstanceOf(AppError);
    expect(auth.isAuthenticated()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'],
      jasmine.objectContaining({ queryParams: jasmine.objectContaining({ reason: 'expired' }) }),
    );
  });

  it('gives up after one retry if the replay is still unauthorized', () => {
    let failure: unknown;
    client.get('/api/v1/merchants/me').subscribe({ error: (error) => (failure = error) });

    http.expectOne('/api/v1/merchants/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush(aSession({ accessToken: 'access-2' }));
    http.expectOne('/api/v1/merchants/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failure).toBeInstanceOf(AppError);
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('signs out straight away when there is no refresh token', () => {
    sessionStorage.clear();
    auth.logout();
    auth.devLogin('user-1').subscribe();
    http.expectOne('/api/v1/dev/tokens').flush({ accessToken: 'dev', expiresIn: 3600 });
    let failure: unknown;

    client.get('/api/v1/merchants/me').subscribe({ error: (error) => (failure = error) });
    http.expectOne('/api/v1/merchants/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failure).toBeInstanceOf(AppError);
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('passes other errors through without refreshing', () => {
    let failure: AppError | undefined;
    client.get('/api/v1/merchants/me').subscribe({ error: (error) => (failure = error) });

    http
      .expectOne('/api/v1/merchants/me')
      .flush({ code: 'FORBIDDEN', message: 'no' }, { status: 403, statusText: 'Forbidden' });

    expect(failure?.code).toBe('FORBIDDEN');
    expect(auth.isAuthenticated()).toBeTrue();
  });
});
