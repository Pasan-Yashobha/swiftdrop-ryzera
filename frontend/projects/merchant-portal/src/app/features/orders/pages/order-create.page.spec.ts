import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { MerchantStore } from '../../../core/merchant/merchant.store';
import { signInMerchantUser } from '../../../core/testing/auth';
import { buttonByText, element, inputByLabel, query, type } from '../../../core/testing/dom';
import { aMerchant } from '../../../core/testing/test-data';
import { anOrder } from '../testing/order-data';
import { OrderCreatePage } from './order-create.page';

const CREATE_URL = '/api/v1/merchants/me/orders';

describe('OrderCreatePage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'orders/new', component: OrderCreatePage }],
          withComponentInputBinding(),
        ),
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

  async function open(url = '/orders/new'): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, OrderCreatePage);
  }

  const root = () => element(harness.fixture);
  const field = (label: string) => inputByLabel(harness.fixture, label);

  function fillValidOrder(): void {
    type(field('Recipient name'), 'Nimali Perera');
    type(field('Recipient phone'), '+94 77 123 4567');
    type(field('Pickup address'), '12 Galle Rd');
    type(field('Pickup city'), 'Colombo');
    type(field('Delivery address'), '45 Temple St');
    type(field('Delivery city'), 'Kandy');
    type(field('Delivery postal code'), '20000');
    type(field('Weight (kg)'), '2.5');
    selectSize('SMALL');
    type(field('Your reference'), 'ORDER-1001');
    harness.detectChanges();
  }

  function selectSize(value: string): void {
    const select = query<HTMLSelectElement>(harness.fixture, 'select');
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  function submit(): void {
    query<HTMLFormElement>(harness.fixture, 'form').dispatchEvent(new Event('submit'));
    harness.detectChanges();
  }

  function createRequest(): TestRequest {
    return http.expectOne((request) => request.url === CREATE_URL);
  }

  function errorFor(label: string): string | undefined {
    return field(label).closest('.field')?.querySelector('.field__error')?.textContent?.trim();
  }

  it('has a labelled field for every part of the order and the sandbox switch', async () => {
    await open();

    for (const label of [
      'Recipient name',
      'Recipient phone',
      'Pickup address',
      'Pickup city',
      'Pickup postal code',
      'Delivery address',
      'Delivery city',
      'Delivery postal code',
      'Weight (kg)',
      'Parcel description',
      'Your reference',
      'Notes for the courier',
    ]) {
      expect(field(label)).withContext(label).toBeTruthy();
    }
    const sizes = Array.from(root().querySelectorAll('select option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(sizes).toEqual(['Select a size', 'Small', 'Medium', 'Large']);
    expect(query<HTMLInputElement>(harness.fixture, 'input[type="checkbox"]').checked).toBeFalse();
    expect(root().textContent).toContain('no real parcel is created');
  });

  it('starts the pickup address as the business address', async () => {
    signInMerchantUser(http);
    TestBed.inject(MerchantStore).ensureLoaded().subscribe();
    http
      .expectOne('/api/v1/merchants/me')
      .flush(aMerchant({ addressLine: '9 Main St', city: 'Galle' }));

    await open();

    expect(field('Pickup address').value).toBe('9 Main St');
    expect(field('Pickup city').value).toBe('Galle');
    expect(field('Delivery address').value).toBe('');
  });

  it('shows what is missing and sends nothing when submitted empty', async () => {
    await open();

    submit();

    http.expectNone((request) => request.url === CREATE_URL);
    expect(errorFor('Recipient name')).toBe('Recipient name is required.');
    expect(errorFor('Recipient phone')).toBe('Recipient phone is required.');
    expect(errorFor('Weight (kg)')).toBe('Weight (kg) is required.');
    expect(
      root().querySelector('select')?.closest('.field')?.querySelector('.field__error')
        ?.textContent,
    ).toContain('Choose a parcel size.');
    expect(document.activeElement).toBe(field('Recipient name'));
    expect(field('Recipient name').getAttribute('aria-invalid')).toBe('true');
  });

  it('explains invalid phone, weight and reference values', async () => {
    await open();

    type(field('Recipient phone'), '12345');
    type(field('Weight (kg)'), '2.555');
    type(field('Your reference'), 'has space');
    type(field('Notes for the courier'), 'tab\there');
    harness.detectChanges();

    expect(errorFor('Recipient phone')).toBe(
      'Enter a phone number with 9 to 15 digits, for example +94771234567.',
    );
    expect(errorFor('Weight (kg)')).toBe('Use at most 2 decimal places, for example 2.25.');
    expect(errorFor('Your reference')).toBe('Use only letters, numbers and . _ : / # -');
    expect(errorFor('Notes for the courier')).toBe(
      'Notes for the courier contains characters that are not allowed.',
    );

    type(field('Weight (kg)'), '51');
    type(field('Recipient name'), 'x'.repeat(121));
    harness.detectChanges();
    expect(errorFor('Weight (kg)')).toBe('Weight (kg) must be at most 50.');
    expect(errorFor('Recipient name')).toBe('Recipient name must be at most 120 characters.');
  });

  it('sends the order as a live order with the phone number normalised and optional blanks left out', async () => {
    await open();
    fillValidOrder();

    submit();

    const request = createRequest();
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('sandbox')).toBe('false');
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    // JSON.stringify drops the undefined optional fields exactly as the wire format does.
    expect(JSON.parse(JSON.stringify(request.request.body))).toEqual({
      externalRef: 'ORDER-1001',
      recipient: { name: 'Nimali Perera', phone: '+94771234567' },
      pickup: { addressLine: '12 Galle Rd', city: 'Colombo' },
      dropoff: { addressLine: '45 Temple St', city: 'Kandy', postalCode: '20000' },
      parcel: { weightKg: 2.5, size: 'SMALL' },
    });
    request.flush(anOrder(), { status: 201, statusText: 'Created' });
  });

  it('disables the button while submitting and ignores a second submit', async () => {
    await open();
    fillValidOrder();

    submit();
    const button = buttonByText(harness.fixture, 'Creating order...');
    submit();

    expect(button.disabled).toBeTrue();
    const request = createRequest();
    request.flush(anOrder());
    harness.detectChanges();
    http.expectNone((r) => r.url === CREATE_URL);
  });

  it('shows the tracking number with copy buttons, the tracking link and next actions on success', async () => {
    await open();
    fillValidOrder();
    submit();

    createRequest().flush(anOrder(), { status: 201, statusText: 'Created' });
    harness.detectChanges();

    expect(root().querySelector('form')).toBeNull();
    expect(root().querySelector('h2')?.textContent).toBe('Order created');
    expect(document.activeElement).toBe(root().querySelector('#created-heading'));
    expect(root().querySelector('.mono')?.textContent).toBe('SD24000123');
    expect(
      Array.from(root().querySelectorAll('app-copy-button')).map((b) => b.textContent?.trim()),
    ).toEqual(['Copy number', 'Copy link']);
    const link = query<HTMLAnchorElement>(harness.fixture, 'dd a');
    expect(link.href).toBe('http://localhost:4200/track/SD24000123');
    expect(link.rel).toContain('noopener');
    expect(root().textContent).toContain('LKR 450.00');
    expect(buttonByText(harness.fixture, 'Create another order')).toBeTruthy();
    expect(
      Array.from(root().querySelectorAll('a.btn-primary')).map((a) => a.getAttribute('href')),
    ).toContain('/orders/a1b2c3d4-0000-4000-8000-000000000001');
  });

  it('starts a clean form for another order and keeps the chosen environment', async () => {
    await open();
    fillValidOrder();
    query<HTMLInputElement>(harness.fixture, 'input[type="checkbox"]').click();
    submit();
    createRequest().flush(anOrder({ sandbox: true }), { status: 201, statusText: 'Created' });
    harness.detectChanges();
    expect(root().textContent).toContain('This is a sandbox order');

    buttonByText(harness.fixture, 'Create another order').click();
    harness.detectChanges();

    expect(field('Recipient name').value).toBe('');
    expect(field('Weight (kg)').value).toBe('');
    expect(query<HTMLInputElement>(harness.fixture, 'input[type="checkbox"]').checked).toBeTrue();
    expect(errorFor('Recipient name')).toBeUndefined();
  });

  describe('environment', () => {
    it('creates a sandbox order when the switch is on', async () => {
      await open();
      fillValidOrder();
      query<HTMLInputElement>(harness.fixture, 'input[type="checkbox"]').click();

      submit();

      const request = createRequest();
      expect(request.request.params.get('sandbox')).toBe('true');
      request.flush(anOrder({ sandbox: true }));
    });

    it('opens in sandbox mode when the link asks for it', async () => {
      await open('/orders/new?sandbox=true');

      expect(query<HTMLInputElement>(harness.fixture, 'input[type="checkbox"]').checked).toBeTrue();
    });
  });

  describe('Idempotency-Key', () => {
    function failWith(request: TestRequest, status: number, code: string): void {
      request.flush({ code, message: 'Something' }, { status, statusText: 'Error' });
      harness.detectChanges();
    }

    it('is reused when the same order is sent again after a failure', async () => {
      await open();
      fillValidOrder();
      submit();
      const first = createRequest();
      const key = first.request.headers.get('Idempotency-Key');
      failWith(first, 503, 'PARCEL_SERVICE_UNAVAILABLE');

      submit();
      const second = createRequest();

      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(anOrder());
    });

    it('is reused by the "Try again" button of an unavailable delivery service', async () => {
      await open();
      fillValidOrder();
      submit();
      const first = createRequest();
      const key = first.request.headers.get('Idempotency-Key');
      failWith(first, 503, 'PARCEL_SERVICE_UNAVAILABLE');

      expect(root().querySelector('[role="alert"]')?.textContent).toContain(
        'delivery service is not available right now, so your order was not created',
      );
      buttonByText(harness.fixture, 'Try again').click();

      const second = createRequest();
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(anOrder());
    });

    it('changes when the order was edited after a failure', async () => {
      await open();
      fillValidOrder();
      submit();
      const first = createRequest();
      failWith(first, 503, 'PARCEL_SERVICE_UNAVAILABLE');

      type(field('Delivery city'), 'Galle');
      submit();
      const second = createRequest();

      expect(second.request.headers.get('Idempotency-Key')).not.toBe(
        first.request.headers.get('Idempotency-Key'),
      );
      second.flush(anOrder());
    });

    it('changes when only the environment was switched', async () => {
      await open();
      fillValidOrder();
      submit();
      const first = createRequest();
      failWith(first, 503, 'PARCEL_SERVICE_UNAVAILABLE');

      query<HTMLInputElement>(harness.fixture, 'input[type="checkbox"]').click();
      submit();
      const second = createRequest();

      expect(second.request.headers.get('Idempotency-Key')).not.toBe(
        first.request.headers.get('Idempotency-Key'),
      );
      second.flush(anOrder({ sandbox: true }));
    });

    it('is new for the next order after a success, even with the same details', async () => {
      await open();
      fillValidOrder();
      submit();
      const first = createRequest();
      first.flush(anOrder());
      harness.detectChanges();
      buttonByText(harness.fixture, 'Create another order').click();
      harness.detectChanges();
      fillValidOrder();

      submit();
      const second = createRequest();

      expect(second.request.headers.get('Idempotency-Key')).not.toBe(
        first.request.headers.get('Idempotency-Key'),
      );
      second.flush(anOrder());
    });
  });

  describe('server errors', () => {
    it('shows a duplicate reference next to the field and moves focus there', async () => {
      await open();
      fillValidOrder();
      submit();

      createRequest().flush(
        { code: 'DUPLICATE_EXTERNAL_REF', message: 'dup' },
        { status: 409, statusText: 'Conflict' },
      );
      harness.detectChanges();

      expect(errorFor('Your reference')).toBe(
        'You already have an order with this reference. Use a different reference or leave it empty.',
      );
      expect(document.activeElement).toBe(field('Your reference'));
      expect(root().querySelector('[role="alert"]')?.textContent).toContain('different reference');
      expect(buttonByText(harness.fixture, 'Create order').disabled).toBeFalse();

      type(field('Your reference'), 'ORDER-1002');
      harness.detectChanges();
      expect(errorFor('Your reference')).toBeUndefined();
    });

    it('explains a rejected parcel without offering a blind retry', async () => {
      await open();
      fillValidOrder();
      submit();

      createRequest().flush(
        { code: 'PARCEL_REJECTED', message: 'Destination is outside the service area.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
      harness.detectChanges();

      const alert = root().querySelector('[role="alert"]');
      expect(alert?.textContent).toContain(
        'could not accept this parcel: Destination is outside the service area. Check the addresses',
      );
      expect(alert?.querySelector('button')).toBeNull();
    });

    it('maps validation details to the matching fields and lists the rest', async () => {
      await open();
      fillValidOrder();
      submit();

      createRequest().flush(
        {
          code: 'VALIDATION_FAILED',
          message: 'Validation failed',
          details: [
            { field: 'dropoff.city', message: 'is outside the delivery area' },
            { field: 'somethingElse', message: 'is odd' },
          ],
        },
        { status: 400, statusText: 'Bad Request' },
      );
      harness.detectChanges();

      expect(errorFor('Delivery city')).toBe('is outside the delivery area');
      const alert = root().querySelector('[role="alert"]');
      expect(alert?.textContent).toContain('Fix the highlighted fields');
      expect(alert?.querySelector('li')?.textContent).toBe('somethingElse: is odd');
    });

    it('shows the support reference of a failure', async () => {
      await open();
      fillValidOrder();
      submit();

      createRequest().flush(
        { code: 'INTERNAL_ERROR', message: 'x', correlationId: 'corr-123' },
        { status: 500, statusText: 'Server Error' },
      );
      harness.detectChanges();

      expect(root().querySelector('[role="alert"]')?.textContent).toContain('corr-123');
    });
  });
});
