import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { MerchantStore } from '../../core/merchant/merchant.store';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { aMerchant, aSession } from '../../core/testing/test-data';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { OnboardingPage } from './onboarding.page';

describe('OnboardingPage', () => {
  let http: HttpTestingController;
  let router: Router;
  let fixture: ComponentFixture<OnboardingPage>;

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
    spyOn(router, 'navigate').and.resolveTo(true);
    TestBed.inject(AuthService)
      .login({ email: 'owner@lankacrafts.example', password: 'x' })
      .subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());
    fixture = TestBed.createComponent(OnboardingPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function fillValid(): void {
    type(inputByLabel(fixture, 'Business name'), '  Lanka Crafts ');
    type(inputByLabel(fixture, 'Contact phone'), '+94 (77) 123-4567');
    type(inputByLabel(fixture, 'Address'), '12 Galle Rd');
    type(inputByLabel(fixture, 'City'), 'Colombo');
    fixture.detectChanges();
  }

  function submit(): void {
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('pre-fills the contact email from the signed-in user', () => {
    expect(inputByLabel(fixture, 'Contact email').value).toBe('owner@lankacrafts.example');
  });

  it('validates the business rules before submitting', () => {
    type(inputByLabel(fixture, 'Business name'), 'A');
    type(inputByLabel(fixture, 'Contact email'), 'nope');
    type(inputByLabel(fixture, 'Contact phone'), '123');
    type(inputByLabel(fixture, 'Website'), 'ftp://x.lk');
    type(inputByLabel(fixture, 'Business registration number'), 'R'.repeat(65));
    submit();

    http.expectNone('/api/v1/merchants/register');
    const text = element(fixture).textContent ?? '';
    expect(text).toContain('Business name must be at least 2 characters.');
    expect(text).toContain('Enter a valid email address.');
    expect(text).toContain('Enter a phone number with 9 to 15 digits');
    expect(text).toContain('Enter a full web address');
    expect(text).toContain('must be at most 64 characters');
    expect(text).toContain('Address is required.');
    expect(text).toContain('City is required.');
  });

  it('enforces the maximum lengths', () => {
    fillValid();
    type(inputByLabel(fixture, 'Business name'), 'N'.repeat(151));
    type(inputByLabel(fixture, 'Address'), 'A'.repeat(256));
    type(inputByLabel(fixture, 'City'), 'C'.repeat(101));
    submit();

    http.expectNone('/api/v1/merchants/register');
    const text = element(fixture).textContent ?? '';
    expect(text).toContain('Business name must be at most 150 characters.');
    expect(text).toContain('Address must be at most 255 characters.');
    expect(text).toContain('City must be at most 100 characters.');
  });

  it('registers the business with trimmed, normalised values and moves to the status page', () => {
    const toasts = TestBed.inject(ToastService);
    spyOn(toasts, 'success');
    fillValid();
    submit();

    const request = http.expectOne('/api/v1/merchants/register');
    expect(request.request.body).toEqual({
      businessName: 'Lanka Crafts',
      businessRegNo: null,
      contactEmail: 'owner@lankacrafts.example',
      contactPhone: '+94771234567',
      addressLine: '12 Galle Rd',
      city: 'Colombo',
      website: null,
    });
    request.flush(aMerchant({ status: 'PENDING' }), { status: 201, statusText: 'Created' });

    expect(TestBed.inject(MerchantStore).status()).toBe('PENDING');
    expect(toasts.success).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/status');
  });

  it('shows server-side field errors next to the fields', () => {
    fillValid();
    submit();

    http
      .expectOne('/api/v1/merchants/register')
      .flush(
        {
          code: 'VALIDATION_FAILED',
          message: 'Validation failed',
          details: [{ field: 'contactPhone', message: 'is not a supported number' }],
        },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('is not a supported number');
    expect(buttonByText(fixture, 'Submit for approval').disabled).toBeFalse();
  });

  it('goes to the status page when a business is already registered', () => {
    fillValid();
    submit();

    http
      .expectOne('/api/v1/merchants/register')
      .flush(
        { code: 'MERCHANT_ALREADY_REGISTERED', message: 'exists' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(router.navigateByUrl).toHaveBeenCalledWith('/status');
  });

  it('shows a safe message when the service is down', () => {
    fillValid();
    submit();

    http
      .expectOne('/api/v1/merchants/register')
      .flush({ message: 'psql: connection refused' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('temporarily unavailable');
    expect(element(fixture).textContent).not.toContain('psql');
  });
});
