import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { Receipt } from '../../core/models/document.model';
import { buttonByText, element, inputByLabel, type } from '../../core/testing/dom';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { RECEIPTS_PAGE_SIZE, ReceiptsPage } from './receipts.page';

const RECEIPTS = '/api/v1/receipts';

const receipt: Receipt = {
  id: 'r-1',
  receiptNo: 'RCP-2026-000001',
  parcelId: 'p-1',
  trackingNumber: 'SD-1234-5678',
  deliveredAt: '2026-11-14T04:30:00Z',
  totalAmount: 550,
  currency: 'LKR',
  generatedAt: '2026-11-14T04:31:00Z',
};

describe('ReceiptsPage (admin)', () => {
  let http: HttpTestingController;
  let toasts: ToastService;

  function setUp(production = false): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { production, apiBase: '/api/v1', devLogin: false } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    spyOn(toasts, 'success');
    spyOn(toasts, 'error');
  }

  afterEach(() => http.verify());

  const list = () => http.expectOne((r) => r.url === RECEIPTS && r.method === 'GET');

  function create(items: Receipt[], totalElements = items.length): ComponentFixture<ReceiptsPage> {
    const fixture = TestBed.createComponent(ReceiptsPage);
    fixture.detectChanges();
    list().flush({ items, page: 0, size: RECEIPTS_PAGE_SIZE, totalElements });
    fixture.detectChanges();
    return fixture;
  }

  it('lists every receipt with its total in Sri Lanka time', () => {
    setUp();
    const fixture = create([receipt]);
    const row = element(fixture).querySelector('tbody tr') as HTMLElement;

    expect(row.textContent).toContain('RCP-2026-000001');
    expect(row.textContent).toContain('14 Nov 2026, 10:00');
    expect(row.textContent).toContain('LKR 550.00');
  });

  it('shows an empty state and pages through long lists', () => {
    setUp();
    expect(element(create([])).textContent).toContain('No receipts yet');

    const fixture = create([receipt], 45);
    expect(element(fixture).querySelector('app-pagination')).toBeTruthy();
  });

  it('downloads a receipt PDF', () => {
    setUp();
    const fixture = create([receipt]);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');

    buttonByText(fixture, 'Download PDF').click();
    http.expectOne(`${RECEIPTS}/r-1/download`).flush(new Blob(['%PDF']));

    expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe(
      'receipt-RCP-2026-000001.pdf',
    );
  });

  it('reports a failed download', () => {
    setUp();
    const fixture = create([receipt]);
    buttonByText(fixture, 'Download PDF').click();
    http
      .expectOne(`${RECEIPTS}/r-1/download`)
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(toasts.error).toHaveBeenCalled();
  });

  it('creates a test receipt whose total adds up the charges', () => {
    setUp();
    const fixture = create([]);
    type(inputByLabel(fixture, 'Customer id'), ' a0000000-0000-0000-0000-000000000004 ');
    type(inputByLabel(fixture, 'Discount'), '50');
    buttonByText(fixture, 'Create receipt').click();

    const post = http.expectOne('/api/v1/dev/receipts');
    expect(post.request.body).toEqual(
      jasmine.objectContaining({
        customerId: 'a0000000-0000-0000-0000-000000000004',
        baseAmount: 300,
        weightAmount: 200,
        sizeAmount: 50,
        discountAmount: 50,
        totalAmount: 500,
      }),
    );
    post.flush(receipt);
    list().flush({ items: [receipt], page: 0, size: RECEIPTS_PAGE_SIZE, totalElements: 1 });

    expect(toasts.success).toHaveBeenCalledWith('Receipt RCP-2026-000001 created.');
  });

  it('does not send a test receipt without a customer id', () => {
    setUp();
    const fixture = create([]);
    buttonByText(fixture, 'Create receipt').click();
    fixture.detectChanges();

    http.expectNone('/api/v1/dev/receipts');
    expect(element(fixture).textContent).toContain('Customer id is required.');
  });

  it('hides the test form in production builds', () => {
    setUp(true);
    const fixture = create([]);
    expect(element(fixture).textContent).not.toContain('Create a test receipt');
  });
});
