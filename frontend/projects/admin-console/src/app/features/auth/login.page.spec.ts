import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG, AppConfig } from '../../core/config/app-config';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { buttonByText, element, query, type } from '../../core/testing/dom';
import { aSession } from '../../core/testing/test-data';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let http: HttpTestingController;
  let router: Router;

  function create(devLogin: boolean): ComponentFixture<LoginPage> {
    const config: AppConfig = { production: false, apiBase: '/api/v1', devLogin };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: config },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('signs an admin in and goes to the return URL', () => {
    const fixture = create(false);
    fixture.componentRef.setInput('returnUrl', '/admin/webhooks');

    type(query<HTMLInputElement>(fixture, '#login-email'), 'ops@swiftdrop.example');
    type(query<HTMLInputElement>(fixture, '#login-password'), 'Secret123');
    buttonByText(fixture, 'Sign in').click();
    http.expectOne('/api/v1/auth/login').flush(aSession());

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/webhooks');
  });

  it('sends a non-admin to the not-authorised page', () => {
    const fixture = create(false);

    type(query<HTMLInputElement>(fixture, '#login-email'), 'shop@example.com');
    type(query<HTMLInputElement>(fixture, '#login-password'), 'Secret123');
    buttonByText(fixture, 'Sign in').click();
    http.expectOne('/api/v1/auth/login').flush(aSession({}, 'MERCHANT'));

    expect(router.navigateByUrl).toHaveBeenCalledWith('/not-authorised');
  });

  it('shows a clear message for wrong credentials', () => {
    const fixture = create(false);

    type(query<HTMLInputElement>(fixture, '#login-email'), 'ops@swiftdrop.example');
    type(query<HTMLInputElement>(fixture, '#login-password'), 'nope');
    buttonByText(fixture, 'Sign in').click();
    http
      .expectOne('/api/v1/auth/login')
      .flush({ code: 'UNAUTHORIZED', message: 'Bad credentials' }, { status: 401, statusText: 'x' });
    fixture.detectChanges();

    expect(query(fixture, '[role="alert"]').textContent).toContain(
      'The email or password is incorrect.',
    );
  });

  it('hides the dev sign-in unless the environment enables it', () => {
    const fixture = create(false);

    expect(element(fixture).textContent).not.toContain('Development sign-in');
  });

  it('dev sign-in gets an ADMIN token for the chosen subject', () => {
    const fixture = create(true);

    type(query<HTMLInputElement>(fixture, '#dev-subject'), 'admin-7');
    buttonByText(fixture, 'Sign in as dev admin').click();
    const request = http.expectOne('/api/v1/dev/tokens');
    expect(request.request.body).toEqual({ sub: 'admin-7', role: 'ADMIN' });
    request.flush({ accessToken: 'dev', expiresIn: 3600 });

    expect(TestBed.inject(AuthService).isAdmin()).toBeTrue();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/notifications');
  });
});
