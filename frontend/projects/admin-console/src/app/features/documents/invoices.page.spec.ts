import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { Invoice, InvoiceDetail } from '../../core/models/document.model';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { INVOICES_PAGE_SIZE, InvoicesPage } from './invoices.page';

const INVOICES = '/api/v1/invoices';

const invoice: Invoice = {
  id: 'i-1',
  invoiceNo: 'INV-2026-000001',
  merchantId: 'a0000000-0000-0000-0000-000000000002',
  merchantName: 'Lanka Crafts',
  periodStart: '2026-10-01',
  periodEnd: '2026-10-31',
  totalAmount: 1237.5,
  currency: 'LKR',
  lineCount: 2,
  generatedAt: '2026-11-01T02:00:00Z',
};

const detail: InvoiceDetail = {
  invoice,
  lines: [
    {
      lineNo: 1,
      parcelId: 'p-1',
      trackingNumber: 'SD-1',
      description: 'Colombo to Kandy, Standard',
      amount: 550,
    },
    {
      lineNo: 2,
      parcelId: 'p-2',
      trackingNumber: 'SD-2',
      description: 'Colombo to Kandy, Express',
      amount: 687.5,
    },
  ],
};

describe('InvoicesPage (admin)', () => {
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

  const list = () => http.expectOne((r) => r.url === INVOICES && r.method === 'GET');

  function create(items: Invoice[]): ComponentFixture<InvoicesPage> {
    const fixture = TestBed.createComponent(InvoicesPage);
    fixture.detectChanges();
    list().flush({ items, page: 0, size: INVOICES_PAGE_SIZE, totalElements: items.length });
    fixture.detectChanges();
    return fixture;
  }

  it('lists every invoice with the merchant name', () => {
    setUp();
    const fixture = create([invoice]);
    const row = element(fixture).querySelector('tbody tr') as HTMLElement;

    expect(element(fixture).querySelector('thead')?.textContent).toContain('Merchant');
    expect(row.textContent).toContain('Lanka Crafts');
    expect(row.textContent).toContain('2026-10-01 → 2026-10-31');
    expect(row.textContent).toContain('LKR 1,237.50');
  });

  it('shows and hides the billed lines of an invoice', () => {
    setUp();
    const fixture = create([invoice]);

    buttonByText(fixture, 'Lines').click();
    http.expectOne(`${INVOICES}/i-1`).flush(detail);
    fixture.detectChanges();
    const section = query(fixture, 'section[aria-label="Invoice lines"]');
    expect(section.textContent).toContain('Colombo to Kandy, Express');
    expect(section.textContent).toContain('LKR 687.50');

    buttonByText(fixture, 'Hide lines').click();
    fixture.detectChanges();
    expect(element(fixture).querySelector('section[aria-label="Invoice lines"]')).toBeNull();
  });

  it('reports lines that cannot be loaded', () => {
    setUp();
    const fixture = create([invoice]);
    buttonByText(fixture, 'Lines').click();
    http.expectOne(`${INVOICES}/i-1`).flush(null, { status: 503, statusText: 'Unavailable' });

    expect(toasts.error).toHaveBeenCalled();
  });

  it('downloads the invoice PDF', () => {
    setUp();
    const fixture = create([invoice]);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');

    buttonByText(fixture, 'PDF').click();
    http.expectOne(`${INVOICES}/i-1/download`).flush(new Blob(['%PDF']));

    expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe(
      'invoice-INV-2026-000001.pdf',
    );
  });

  it('generates a test invoice from the prefilled lines', () => {
    setUp();
    const fixture = create([]);
    type(inputByLabel(fixture, 'Merchant id'), 'a0000000-0000-0000-0000-000000000002');
    type(inputByLabel(fixture, 'Merchant name'), ' Lanka Crafts ');
    buttonByText(fixture, 'Create invoice').click();

    const post = http.expectOne(`${INVOICES}/generate`);
    expect(post.request.body.merchantName).toBe('Lanka Crafts');
    expect(post.request.body.lines.length).toBe(2);
    expect(post.request.body.lines[1].amount).toBe(687.5);
    post.flush(invoice);
    list().flush({ items: [invoice], page: 0, size: INVOICES_PAGE_SIZE, totalElements: 1 });

    expect(toasts.success).toHaveBeenCalledWith(
      'Invoice INV-2026-000001 created for Lanka Crafts.',
    );
  });

  it('adds and removes billing lines and needs at least one', () => {
    setUp();
    const fixture = create([]);
    buttonByText(fixture, 'Add line').click();
    fixture.detectChanges();
    expect(element(fixture).textContent).toContain('Billing lines (3 / 50)');

    for (const label of ['Remove line 3', 'Remove line 2', 'Remove line 1']) {
      query(fixture, `[aria-label="${label}"]`).click();
      fixture.detectChanges();
    }
    expect(buttonByText(fixture, 'Create invoice').disabled).toBeTrue();
  });

  it('shows a server error from invoice generation', () => {
    setUp();
    const fixture = create([]);
    type(inputByLabel(fixture, 'Merchant id'), 'm-1');
    type(inputByLabel(fixture, 'Merchant name'), 'Lanka Crafts');
    buttonByText(fixture, 'Create invoice').click();
    http
      .expectOne(`${INVOICES}/generate`)
      .flush(
        { status: 409, code: 'PARCEL_ALREADY_BILLED', message: 'A parcel is already billed.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('A parcel is already billed.');
  });

  it('hides the test form in production builds', () => {
    setUp(true);
    const fixture = create([]);
    expect(element(fixture).textContent).not.toContain('Create a test invoice');
  });
});
