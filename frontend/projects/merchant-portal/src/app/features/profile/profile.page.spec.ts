import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { MerchantStore } from '../../core/merchant/merchant.store';
import { Merchant } from '../../core/models/merchant.model';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { signInMerchantUser } from '../../core/testing/auth';
import { aMerchant } from '../../core/testing/test-data';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { ProfilePage } from './profile.page';

describe('ProfilePage', () => {
  let http: HttpTestingController;

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
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function create(merchant: Merchant = aMerchant()): ComponentFixture<ProfilePage> {
    signInMerchantUser(http);
    TestBed.inject(MerchantStore).ensureLoaded().subscribe();
    http.expectOne('/api/v1/merchants/me').flush(merchant);
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the business details', () => {
    const fixture = create();
    const text = element(fixture).textContent ?? '';

    expect(text).toContain('Lanka Crafts');
    expect(text).toContain('owner@lankacrafts.example');
    expect(text).toContain('+94771234567');
    expect(text).toContain('12 Galle Rd, Colombo');
    expect(text).toContain('60 requests per minute');
    expect(element(fixture).querySelector('app-status-badge')?.textContent).toContain('Approved');
  });

  it('says so when optional details were not provided', () => {
    const fixture = create(aMerchant({ businessRegNo: null, website: null }));

    expect(element(fixture).textContent).toContain('Not provided');
  });

  it('edits the profile and shows the saved result', () => {
    const toasts = TestBed.inject(ToastService);
    spyOn(toasts, 'success');
    const fixture = create();

    buttonByText(fixture, 'Edit details').click();
    fixture.detectChanges();
    expect(inputByLabel(fixture, 'City').value).toBe('Colombo');
    type(inputByLabel(fixture, 'City'), 'Kandy');
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));

    const request = http.expectOne('/api/v1/merchants/me');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.city).toBe('Kandy');
    request.flush(aMerchant({ city: 'Kandy' }));
    fixture.detectChanges();

    expect(toasts.success).toHaveBeenCalledWith('Profile updated.');
    expect(element(fixture).textContent).toContain('12 Galle Rd, Kandy');
    expect(element(fixture).querySelector('form')).toBeNull();
  });

  it('discards edits on cancel', () => {
    const fixture = create();

    buttonByText(fixture, 'Edit details').click();
    fixture.detectChanges();
    buttonByText(fixture, 'Cancel').click();
    fixture.detectChanges();

    http.expectNone('/api/v1/merchants/me');
    expect(element(fixture).querySelector('form')).toBeNull();
  });

  it('shows the server error and keeps the form open when saving fails', () => {
    const fixture = create();

    buttonByText(fixture, 'Edit details').click();
    fixture.detectChanges();
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/v1/merchants/me')
      .flush(
        { code: 'OPTIMISTIC_LOCK', message: 'Changed by someone else' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Changed by someone else');
    expect(element(fixture).querySelector('form')).not.toBeNull();
  });

  it('links a rejected merchant to the resubmit flow', () => {
    const fixture = create(aMerchant({ status: 'REJECTED' }));

    expect(element(fixture).textContent).toContain('Resubmit application');
  });

  it('shows the load error with a retry', () => {
    signInMerchantUser(http);
    TestBed.inject(MerchantStore)
      .refresh()
      .subscribe({ error: () => undefined });
    http.expectOne('/api/v1/merchants/me').flush(null, { status: 503, statusText: 'Unavailable' });
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();

    buttonByText(fixture, 'Try again').click();
    http.expectOne('/api/v1/merchants/me').flush(aMerchant());
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Lanka Crafts');
  });
});
