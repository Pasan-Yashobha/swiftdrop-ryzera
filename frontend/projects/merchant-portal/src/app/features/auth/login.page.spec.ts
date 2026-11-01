import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor } from '../../core/auth/auth.interceptor';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { aSession } from '../../core/testing/test-data';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let http: HttpTestingController;
  let router: Router;

  function create(options: { devLogin?: boolean; returnUrl?: string; reason?: string } = {}) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: APP_CONFIG,
          useValue: { production: false, apiBase: '/api/v1', devLogin: options.devLogin ?? false },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    spyOn(router, 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(LoginPage);
    if (options.returnUrl) {
      fixture.componentRef.setInput('returnUrl', options.returnUrl);
    }
    if (options.reason) {
      fixture.componentRef.setInput('reason', options.reason);
    }
    fixture.detectChanges();
    return fixture;
  }

  function fillAndSubmit(
    fixture: ReturnType<typeof create>,
    email = 'owner@lankacrafts.example',
    password = 'Secret123',
  ): void {
    type(inputByLabel(fixture, 'Email'), email);
    type(inputByLabel(fixture, 'Password'), password);
    fixture.detectChanges();
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('does not call the server and explains what is missing when the form is empty', () => {
    const fixture = create();

    fillAndSubmit(fixture, '', '');

    http.expectNone('/api/v1/auth/login');
    expect(element(fixture).textContent).toContain('Email is required.');
    expect(element(fixture).textContent).toContain('Password is required.');
  });

  it('rejects a malformed email without calling the server', () => {
    const fixture = create();

    fillAndSubmit(fixture, 'not-an-email');

    http.expectNone('/api/v1/auth/login');
    expect(element(fixture).textContent).toContain('Enter a valid email address.');
  });

  it('signs in and continues to the dashboard', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.body).toEqual({
      email: 'owner@lankacrafts.example',
      password: 'Secret123',
    });
    request.flush(aSession());

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('returns to the page the user came from', () => {
    const fixture = create({ returnUrl: '/orders/42' });

    fillAndSubmit(fixture);
    http.expectOne('/api/v1/auth/login').flush(aSession());

    expect(router.navigateByUrl).toHaveBeenCalledWith('/orders/42');
  });

  it('ignores a return URL that points off-site', () => {
    const fixture = create({ returnUrl: 'https://evil.example' });

    fillAndSubmit(fixture);
    http.expectOne('/api/v1/auth/login').flush(aSession());

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('shows one generic message for wrong credentials and lets the user retry', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    http
      .expectOne('/api/v1/auth/login')
      .flush({ message: 'No such user' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(query(fixture, '[role="alert"]').textContent).toContain(
      'The email or password is incorrect.',
    );
    expect(element(fixture).textContent).not.toContain('No such user');
    expect(buttonByText(fixture, 'Sign in').disabled).toBeFalse();
  });

  it('shows a safe message when the server fails', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    http
      .expectOne('/api/v1/auth/login')
      .flush(
        { code: 'INTERNAL_ERROR', message: 'stack trace here' },
        { status: 500, statusText: 'Error' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Something went wrong on our side');
    expect(element(fixture).textContent).not.toContain('stack trace');
  });

  it('turns away accounts that are not merchants', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    http
      .expectOne('/api/v1/auth/login')
      .flush(aSession({ user: { ...aSession().user, role: 'CUSTOMER' } }));
    fixture.detectChanges();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    expect(element(fixture).textContent).toContain('merchant accounts only');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('tells users whose session expired', () => {
    const fixture = create({ reason: 'expired' });

    expect(element(fixture).textContent).toContain('Your session expired');
  });

  it('hides the developer sign-in outside development builds', () => {
    const fixture = create({ devLogin: false });

    expect(element(fixture).textContent).not.toContain('Developer sign-in');
  });

  it('offers the developer sign-in in development builds', () => {
    const fixture = create({ devLogin: true });

    buttonByText(fixture, 'Sign in as dev merchant').click();
    const request = http.expectOne('/api/v1/dev/tokens');
    expect(request.request.body).toEqual({ sub: 'user-1', role: 'MERCHANT' });
    request.flush({ accessToken: 'dev-token', expiresIn: 3600 });

    expect(TestBed.inject(AuthService).token()).toBe('dev-token');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });
});
