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

  function create(
    options: { devLogin?: boolean; returnUrl?: string; reason?: string; error?: string } = {},
  ) {
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
    if (options.error) {
      fixture.componentRef.setInput('errorParam', options.error);
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
    buttonByText(fixture, 'Sign in').click();
  }

  afterEach(() => http.verify());

  it('renders email and password inputs and a submit button', () => {
    const fixture = create();

    expect(inputByLabel(fixture, 'Email')).toBeTruthy();
    expect(inputByLabel(fixture, 'Password')).toBeTruthy();
    expect(buttonByText(fixture, 'Sign in')).toBeTruthy();
  });

  it('validates required fields before submitting', () => {
    const fixture = create();

    buttonByText(fixture, 'Sign in').click();

    expect(http.match('/api/v1/auth/login')).toEqual([]);
    expect(element(fixture).textContent).toContain('Please enter a valid email address');
  });

  it('signs in and navigates to the dashboard by default', () => {
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

  it('honours a safe returnUrl after sign-in', () => {
    const fixture = create({ returnUrl: '/orders?status=PENDING' });

    fillAndSubmit(fixture);
    http.expectOne('/api/v1/auth/login').flush(aSession());

    expect(router.navigateByUrl).toHaveBeenCalledWith('/orders?status=PENDING');
  });

  it('ignores unsafe returnUrl values (open redirect defense)', () => {
    const fixture = create({ returnUrl: 'https://attacker.example/phish' });

    fillAndSubmit(fixture);
    http.expectOne('/api/v1/auth/login').flush(aSession());

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('shows invalid credentials errors clearly', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    http
      .expectOne('/api/v1/auth/login')
      .flush(
        { code: 'INVALID_CREDENTIALS', message: 'The email or password is incorrect.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('The email or password is incorrect.');
  });

  it('shows account lockout messages from the server', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    http
      .expectOne('/api/v1/auth/login')
      .flush(
        { code: 'ACCOUNT_LOCKED', message: 'Too many attempts. Account locked for 15 minutes.' },
        { status: 429, statusText: 'Too Many Requests' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Too many attempts');
  });

  it('shows a generic message on 5xx errors without leaking internals', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    http
      .expectOne('/api/v1/auth/login')
      .flush({ message: 'database connection failed' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Something went wrong on our side');
    expect(element(fixture).textContent).not.toContain('stack trace');
  });

  it('redirects accounts that are not merchants to the welcome hub', () => {
    const fixture = create();

    fillAndSubmit(fixture);
    http
      .expectOne('/api/v1/auth/login')
      .flush(aSession({ user: { ...aSession().user, role: 'CUSTOMER' } }));
    fixture.detectChanges();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/welcome');
  });

  it('tells users whose session expired', () => {
    const fixture = create({ reason: 'expired' });

    expect(element(fixture).textContent).toContain('Your session expired');
  });

  it('displays OAuth error banner when error query param is present', () => {
    const fixture = create({ error: 'oauth_denied' });

    expect(element(fixture).textContent).toContain('Sign in with Google was cancelled or denied');
  });

  it('displays CSRF detected error banner', () => {
    const fixture = create({ error: 'csrf_detected' });

    expect(element(fixture).textContent).toContain('Security verification failed');
  });
});
