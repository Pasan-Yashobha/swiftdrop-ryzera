import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { aSession } from '../../core/testing/test-data';
import { SignupPage } from './signup.page';

describe('SignupPage', () => {
  let http: HttpTestingController;
  let router: Router;
  let fixture: ComponentFixture<SignupPage>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(SignupPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function fill(
    values: {
      name?: string;
      email?: string;
      phone?: string;
      password?: string;
      confirm?: string;
    } = {},
  ): void {
    type(inputByLabel(fixture, 'Full name'), values.name ?? 'Nimali Perera');
    type(inputByLabel(fixture, 'Email'), values.email ?? 'owner@lankacrafts.example');
    type(inputByLabel(fixture, 'Phone'), values.phone ?? '+94 77 123 4567');
    type(inputByLabel(fixture, 'Password'), values.password ?? 'Abcdef12');
    type(
      inputByLabel(fixture, 'Confirm password'),
      values.confirm ?? values.password ?? 'Abcdef12',
    );
    fixture.detectChanges();
  }

  function submit(): void {
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('shows the password rules and ticks them off as the user types', () => {
    const rules = () => Array.from(element(fixture).querySelectorAll('.rules li'));
    expect(rules().length).toBe(4);
    expect(rules().every((rule) => !rule.classList.contains('rules__met'))).toBeTrue();

    type(inputByLabel(fixture, 'Password'), 'Abcdef12');
    fixture.detectChanges();

    expect(rules().every((rule) => rule.classList.contains('rules__met'))).toBeTrue();
  });

  it('lets the user show and hide the password', () => {
    const password = inputByLabel(fixture, 'Password');

    buttonByText(fixture, 'Show').click();
    fixture.detectChanges();

    expect(password.type).toBe('text');
  });

  it('validates every field before calling the server', () => {
    submit();

    http.expectNone('/api/v1/auth/signup');
    const text = element(fixture).textContent ?? '';
    expect(text).toContain('Full name is required.');
    expect(text).toContain('Email is required.');
    expect(text).toContain('Phone is required.');
    expect(text).toContain('Password is required.');
  });

  it('rejects a weak password and a mismatched confirmation', () => {
    fill({ password: 'abc', confirm: 'abd' });
    submit();

    http.expectNone('/api/v1/auth/signup');
    expect(element(fixture).textContent).toContain('Choose a password that meets all the rules');
    expect(element(fixture).textContent).toContain('The passwords do not match.');
  });

  it('rejects a phone number that is not valid', () => {
    fill({ phone: '12ab' });
    submit();

    http.expectNone('/api/v1/auth/signup');
    expect(element(fixture).textContent).toContain('Enter a phone number with 9 to 15 digits');
  });

  it('creates a MERCHANT account with a normalised phone number and continues to onboarding', () => {
    fill();
    submit();

    const request = http.expectOne('/api/v1/auth/signup');
    expect(request.request.body).toEqual({
      fullName: 'Nimali Perera',
      email: 'owner@lankacrafts.example',
      phone: '+94771234567',
      password: 'Abcdef12',
      role: 'MERCHANT',
    });
    request.flush(aSession());

    expect(router.navigateByUrl).toHaveBeenCalledWith('/onboarding');
  });

  it('explains a duplicate email without echoing server text', () => {
    fill();
    submit();

    http
      .expectOne('/api/v1/auth/signup')
      .flush(
        { code: 'CONFLICT', message: 'duplicate key value violates' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('An account with this email already exists');
    expect(element(fixture).textContent).not.toContain('duplicate key');
    expect(buttonByText(fixture, 'Create account').disabled).toBeFalse();
  });

  it('shows field errors reported by the server next to the field', () => {
    fill();
    submit();

    http.expectOne('/api/v1/auth/signup').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'Validation failed',
        details: [{ field: 'email', message: 'is not allowed' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('is not allowed');
  });

  it('updates the Google signup link when the selected account type changes', () => {
    const googleLink = query<HTMLAnchorElement>(fixture, 'a.btn-google');
    expect(googleLink.getAttribute('href')).toBe('/api/v1/auth/google?role=MERCHANT');

    fixture.componentInstance['form'].controls.role.setValue('COURIER');
    fixture.detectChanges();

    expect(googleLink.getAttribute('href')).toBe('/api/v1/auth/google?role=COURIER');

    fixture.componentInstance['form'].controls.role.setValue('CUSTOMER');
    fixture.detectChanges();

    expect(googleLink.getAttribute('href')).toBe('/api/v1/auth/google?role=CUSTOMER');
  });

  it('updates live tracking hint when quick track is submitted', () => {
    const trackInput = query<HTMLInputElement>(fixture, '#quickTrackInputSignup');
    trackInput.value = 'SD-8888-5678';
    trackInput.dispatchEvent(new Event('input'));
    buttonByText(fixture, 'Go').click();
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('SD-8888-5678');
    expect(element(fixture).textContent).toContain('In Transit');
  });
});
