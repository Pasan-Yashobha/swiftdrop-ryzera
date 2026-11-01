import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { MerchantStore } from '../../core/merchant/merchant.store';
import { Merchant } from '../../core/models/merchant.model';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { signInMerchantUser } from '../../core/testing/auth';
import { aMerchant } from '../../core/testing/test-data';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { StatusPage } from './status.page';

describe('StatusPage', () => {
  let http: HttpTestingController;
  let router: Router;
  let toasts: ToastService;

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
    toasts = TestBed.inject(ToastService);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    spyOn(toasts, 'success');
    spyOn(toasts, 'info');
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  /** Loads `merchant` into the store (as the route guard does) and renders the page. */
  function create(merchant: Merchant): ComponentFixture<StatusPage> {
    signInMerchantUser(http);
    TestBed.inject(MerchantStore).ensureLoaded().subscribe();
    http.expectOne('/api/v1/merchants/me').flush(merchant);
    const fixture = TestBed.createComponent(StatusPage);
    fixture.detectChanges();
    return fixture;
  }

  it('tells a pending merchant that the application is being reviewed and lets them check again', () => {
    const fixture = create(aMerchant({ status: 'PENDING', businessName: 'Lanka Crafts' }));

    expect(element(fixture).textContent).toContain('Lanka Crafts');
    expect(element(fixture).textContent).toContain('waiting for review');
    expect(element(fixture).querySelector('app-status-badge')?.textContent).toContain('Pending');

    buttonByText(fixture, 'Check status').click();
    http.expectOne('/api/v1/merchants/me').flush(aMerchant({ status: 'PENDING' }));

    expect(toasts.info).toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('moves on to the dashboard once the merchant has been approved', () => {
    const fixture = create(aMerchant({ status: 'PENDING' }));

    buttonByText(fixture, 'Check status').click();
    http.expectOne('/api/v1/merchants/me').flush(aMerchant({ status: 'APPROVED' }));

    expect(toasts.success).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('shows the rejection reason and an editable form for resubmitting', () => {
    const fixture = create(
      aMerchant({
        status: 'REJECTED',
        decisionReason: 'Registration number could not be verified',
        decidedAt: '2026-10-05T10:00:00Z',
      }),
    );

    expect(element(fixture).textContent).toContain('was not approved');
    expect(element(fixture).textContent).toContain('Registration number could not be verified');
    expect(inputByLabel(fixture, 'Business name').value).toBe('Lanka Crafts');
    expect(() => buttonByText(fixture, 'Check status')).toThrow();
  });

  it('saves the corrections and then resubmits the application', () => {
    const fixture = create(aMerchant({ status: 'REJECTED', decisionReason: 'Bad number' }));

    type(inputByLabel(fixture, 'Business registration number'), 'PV99999');
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));

    const update = http.expectOne('/api/v1/merchants/me');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body.businessRegNo).toBe('PV99999');
    update.flush(aMerchant({ status: 'REJECTED', businessRegNo: 'PV99999' }));
    const resubmit = http.expectOne('/api/v1/merchants/me/resubmit');
    expect(resubmit.request.method).toBe('POST');
    resubmit.flush(aMerchant({ status: 'PENDING', decisionReason: null }));
    fixture.detectChanges();

    expect(toasts.success).toHaveBeenCalledWith('Application resubmitted for review.');
    expect(element(fixture).textContent).toContain('waiting for review');
  });

  it('keeps the form and shows the error when resubmitting fails', () => {
    const fixture = create(aMerchant({ status: 'REJECTED' }));

    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/v1/merchants/me')
      .flush(
        { code: 'INVALID_STATE', message: 'Merchant cannot be edited now' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    http.expectNone('/api/v1/merchants/me/resubmit');
    expect(element(fixture).textContent).toContain('Merchant cannot be edited now');
    expect(buttonByText(fixture, 'Save and resubmit').disabled).toBeFalse();
  });

  it('explains a suspension and offers to check again', () => {
    const fixture = create(aMerchant({ status: 'SUSPENDED', decisionReason: 'Chargeback review' }));

    expect(element(fixture).textContent).toContain('Your account is suspended');
    expect(element(fixture).textContent).toContain('Chargeback review');
    expect(element(fixture).textContent).toContain('Contact SwiftDrop support');
    expect(buttonByText(fixture, 'Check status')).toBeTruthy();
  });

  it('shows a loading skeleton before the merchant is known', () => {
    signInMerchantUser(http);
    const fixture = TestBed.createComponent(StatusPage);
    fixture.detectChanges();

    expect(element(fixture).querySelector('app-skeleton')).not.toBeNull();
  });

  it('shows the load error with a retry', () => {
    signInMerchantUser(http);
    const store = TestBed.inject(MerchantStore);
    store.refresh().subscribe({ error: () => undefined });
    http.expectOne('/api/v1/merchants/me').flush(null, { status: 503, statusText: 'Unavailable' });
    const fixture = TestBed.createComponent(StatusPage);
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('temporarily unavailable');
    buttonByText(fixture, 'Try again').click();
    http.expectOne('/api/v1/merchants/me').flush(aMerchant({ status: 'PENDING' }));
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('waiting for review');
  });
});
