import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { PromoCode } from '../../core/models/pricing.model';
import { buttonByText, element, inputByLabel, type } from '../../core/testing/dom';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { PromoCodesPage } from './promo-codes.page';

const URL = '/api/v1/admin/promo-codes';

const save10: PromoCode = {
  id: 'p-1',
  code: 'SAVE10',
  discountType: 'PERCENT',
  discountValue: 10,
  minOrderValue: 500,
  maxUses: 100,
  usedCount: 3,
  validFrom: '2026-01-01',
  validTo: null,
  active: true,
};

const flat200: PromoCode = {
  ...save10,
  id: 'p-2',
  code: 'FLAT200',
  discountType: 'FLAT',
  discountValue: 200,
  maxUses: null,
  active: false,
};

describe('PromoCodesPage', () => {
  let http: HttpTestingController;
  let toasts: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    spyOn(toasts, 'success');
    spyOn(toasts, 'error');
  });

  afterEach(() => {
    document.querySelectorAll('dialog[open]').forEach((d) => (d as HTMLDialogElement).close());
    http.verify();
  });

  const list = () => http.expectOne((r) => r.url === URL && r.method === 'GET');
  const page = (items: PromoCode[]) => ({ items, page: 0, size: 100, totalElements: items.length });

  function create(promos: PromoCode[]): ComponentFixture<PromoCodesPage> {
    const fixture = TestBed.createComponent(PromoCodesPage);
    fixture.detectChanges();
    list().flush(page(promos));
    fixture.detectChanges();
    return fixture;
  }

  function chooseType(fixture: ComponentFixture<PromoCodesPage>, value: 'PERCENT' | 'FLAT'): void {
    const select = inputByLabel(fixture, 'Discount type') as unknown as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('shows percentage and fixed discounts with their usage', () => {
    const fixture = create([save10, flat200]);
    const [first, second] = Array.from(element(fixture).querySelectorAll('tbody tr'));

    expect(first.textContent).toContain('10%');
    expect(first.textContent).toContain('3 / 100');
    expect(second.textContent).toContain('LKR 200.00');
    expect(second.textContent).toContain('∞');
  });

  it('does not allow a percentage above 100', () => {
    const fixture = create([]);
    type(inputByLabel(fixture, 'Code'), 'BIG');
    type(inputByLabel(fixture, 'Discount (%)'), '150');
    buttonByText(fixture, 'Add promo code').click();
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    expect(element(fixture).textContent).toContain('at most 100');
  });

  it('allows a fixed amount above 100 and sends an empty maximum as null', () => {
    const fixture = create([]);
    type(inputByLabel(fixture, 'Code'), 'WELCOME');
    chooseType(fixture, 'FLAT');
    type(inputByLabel(fixture, 'Discount (LKR)'), '150');
    buttonByText(fixture, 'Add promo code').click();

    const post = http.expectOne((r) => r.url === URL && r.method === 'POST');
    expect(post.request.body).toEqual(
      jasmine.objectContaining({
        code: 'WELCOME',
        discountType: 'FLAT',
        discountValue: 150,
        maxUses: null,
        validTo: null,
      }),
    );
    post.flush({ ...flat200, code: 'WELCOME' });
    list().flush(page([]));

    expect(toasts.success).toHaveBeenCalledWith('Promo code WELCOME saved.');
  });

  it('rejects a code with spaces inside', () => {
    const fixture = create([]);
    type(inputByLabel(fixture, 'Code'), 'SAVE 10');
    buttonByText(fixture, 'Add promo code').click();
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    expect(element(fixture).textContent).toContain('Use letters, numbers, - or _ only.');
  });

  it('edits a code with PUT and shows a server error', () => {
    const fixture = create([save10]);
    buttonByText(fixture, 'Edit').click();
    fixture.detectChanges();
    expect(inputByLabel(fixture, 'Code').value).toBe('SAVE10');

    buttonByText(fixture, 'Save changes').click();
    http
      .expectOne((r) => r.url === `${URL}/p-1` && r.method === 'PUT')
      .flush(
        {
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Maximum uses is below the used count.',
        },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Maximum uses is below the used count.');
  });

  it('deactivates a code after confirmation', async () => {
    const fixture = create([save10]);
    buttonByText(fixture, 'Deactivate').click();
    fixture.detectChanges();

    (document.querySelector('dialog[open] .btn-danger') as HTMLButtonElement).click();
    await fixture.whenStable();

    http.expectOne((r) => r.url === `${URL}/p-1` && r.method === 'DELETE').flush(null);
    list().flush(page([]));
    expect(toasts.success).toHaveBeenCalledWith('Promo code SAVE10 deactivated.');
  });
});
