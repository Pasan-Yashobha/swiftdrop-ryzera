import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { buttonByText, element, query, type } from '../../../core/testing/dom';
import { anOrder, aPageOf } from '../testing/order-data';
import { OrdersListPage, SEARCH_DEBOUNCE_MS } from './orders-list.page';

const LIST_URL = '/api/v1/merchants/me/orders';

describe('OrdersListPage', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'orders', component: OrdersListPage }]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(url = '/orders'): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, OrdersListPage);
  }

  function listRequest() {
    return http.expectOne((request) => request.url === LIST_URL);
  }

  function respond(orders = [anOrder()], overrides = {}): void {
    listRequest().flush(aPageOf(orders, overrides));
    harness.detectChanges();
  }

  function select(id: string, value: string): void {
    const control = query<HTMLSelectElement>(harness.fixture, `#${id}`);
    control.value = value;
    control.dispatchEvent(new Event('change'));
  }

  const currentUrl = () => TestBed.inject(Router).url;
  const root = () => element(harness.fixture);
  const waitForDebounce = () =>
    new Promise((resolve) => setTimeout(resolve, SEARCH_DEBOUNCE_MS + 60));

  it('loads the first page of live orders and shows a skeleton while waiting', async () => {
    await open();

    const request = listRequest();
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('status')).toBeFalse();
    expect(request.request.params.has('sandbox')).toBeFalse();
    expect(root().querySelector('app-skeleton')?.textContent).toContain('Loading orders');
    expect(root().querySelector('table')).toBeNull();

    request.flush(aPageOf([anOrder()]));
    harness.detectChanges();

    expect(root().querySelector('app-skeleton')).toBeNull();
    expect(root().querySelectorAll('tbody tr').length).toBe(1);
  });

  it('shows tracking number, reference, recipient, destination, status, fee and age of each order', async () => {
    await open();
    respond([
      anOrder(),
      anOrder({
        id: 'o-2',
        trackingNo: 'SBX123456789',
        externalRef: null,
        sandbox: true,
        status: 'DELIVERED',
        fee: null,
        recipient: { name: 'Kamal Silva', phone: '+94770000000' },
        dropoff: { addressLine: '1 Beach Rd', city: 'Galle', postalCode: null },
      }),
    ]);

    const rows = Array.from(root().querySelectorAll('tbody tr'));
    const first = rows[0];
    expect(first.querySelector('a')?.textContent).toBe('SD24000123');
    expect(first.querySelector('a')?.getAttribute('href')).toBe(
      '/orders/a1b2c3d4-0000-4000-8000-000000000001',
    );
    expect(first.textContent).toContain('Ref ORDER-1001');
    expect(first.querySelector('td[data-label="Recipient"]')?.textContent).toBe('Nimali Perera');
    expect(first.querySelector('td[data-label="Destination"]')?.textContent).toBe('Kandy');
    expect(first.querySelector('td[data-label="Status"]')?.textContent).toContain('Created');
    expect(first.querySelector('td[data-label="Fee"]')?.textContent?.trim()).toBe('LKR 450.00');
    const created = first.querySelector('time');
    expect(created?.getAttribute('datetime')).toBe('2026-10-30T08:00:00Z');
    expect(created?.getAttribute('title')).toMatch(/2026/);
    expect(created?.textContent?.trim()).not.toBe('');

    const second = rows[1];
    expect(second.textContent).toContain('Sandbox');
    expect(second.textContent).not.toContain('Ref ');
    expect(second.textContent).toContain('Not priced');
    expect(second.querySelector('td[data-label="Status"]')?.textContent).toContain('Delivered');
  });

  it('has a column header for each column and a hidden caption', async () => {
    await open();
    respond();

    const headers = Array.from(root().querySelectorAll('th[scope="col"]')).map(
      (th) => th.textContent,
    );
    expect(headers).toEqual([
      'Tracking number',
      'Recipient',
      'Destination',
      'Status',
      'Fee',
      'Created',
    ]);
    expect(root().querySelector('caption')?.textContent).toContain('Orders');
  });

  it('reads the filters from the URL into the form and the API request', async () => {
    await open(
      '/orders?status=FAILED&search=perera&sandbox=all&from=2026-10-01&to=2026-10-31&page=3',
    );

    const params = listRequest().request.params;
    expect(params.get('status')).toBe('FAILED');
    expect(params.get('search')).toBe('perera');
    expect(params.get('sandbox')).toBe('all');
    expect(params.get('from')).toBe('2026-10-01');
    expect(params.get('to')).toBe('2026-10-31');
    expect(params.get('page')).toBe('2');
    expect(query<HTMLSelectElement>(harness.fixture, '#filter-status').value).toBe('FAILED');
    expect(query<HTMLInputElement>(harness.fixture, '#filter-search').value).toBe('perera');
    expect(query<HTMLSelectElement>(harness.fixture, '#filter-sandbox').value).toBe('all');
    expect(query<HTMLInputElement>(harness.fixture, '#filter-from').value).toBe('2026-10-01');
    expect(query<HTMLInputElement>(harness.fixture, '#filter-to').value).toBe('2026-10-31');
  });

  it('puts a chosen status into the URL, goes back to page one and queries again', async () => {
    await open('/orders?page=2');
    respond();

    select('filter-status', 'DELIVERED');
    await harness.fixture.whenStable();

    expect(currentUrl()).toBe('/orders?status=DELIVERED');
    const params = listRequest().request.params;
    expect(params.get('status')).toBe('DELIVERED');
    expect(params.get('page')).toBe('0');
  });

  it('offers the sandbox, live and combined views', async () => {
    await open();
    respond();

    select('filter-sandbox', 'sandbox');
    await harness.fixture.whenStable();

    expect(currentUrl()).toBe('/orders?sandbox=sandbox');
    expect(listRequest().request.params.get('sandbox')).toBe('true');
  });

  it('waits for typing to pause before searching and sends one request', async () => {
    await open();
    respond();

    const search = query<HTMLInputElement>(harness.fixture, '#filter-search');
    type(search, 'p');
    type(search, 'pe');
    type(search, ' per ');
    http.expectNone((request) => request.url === LIST_URL);
    await waitForDebounce();
    await harness.fixture.whenStable();

    expect(currentUrl()).toBe('/orders?search=per');
    expect(listRequest().request.params.get('search')).toBe('per');
    expect(search.value).toBe(' per ');
  });

  it('applies the date range', async () => {
    await open();
    respond();

    type(query<HTMLInputElement>(harness.fixture, '#filter-from'), '2026-10-01');
    await harness.fixture.whenStable();
    expect(listRequest().request.params.get('from')).toBe('2026-10-01');
    harness.detectChanges();

    type(query<HTMLInputElement>(harness.fixture, '#filter-to'), '2026-10-31');
    await harness.fixture.whenStable();

    expect(currentUrl()).toBe('/orders?from=2026-10-01&to=2026-10-31');
    expect(listRequest().request.params.get('to')).toBe('2026-10-31');
    harness.detectChanges();
    expect(query<HTMLInputElement>(harness.fixture, '#filter-from').max).toBe('2026-10-31');
  });

  it('follows the URL when it changes from outside, as with the back button', async () => {
    await open('/orders?status=FAILED');
    respond();

    await harness.navigateByUrl('/orders?status=DELIVERED&search=kamal');

    expect(listRequest().request.params.get('status')).toBe('DELIVERED');
    expect(query<HTMLSelectElement>(harness.fixture, '#filter-status').value).toBe('DELIVERED');
    expect(query<HTMLInputElement>(harness.fixture, '#filter-search').value).toBe('kamal');
  });

  it('keeps showing the previous results, dimmed, while a new page loads', async () => {
    await open();
    respond();

    select('filter-status', 'FAILED');
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(root().querySelector('.table-wrap')?.classList).toContain('is-loading');
    expect(root().querySelector('section')?.getAttribute('aria-busy')).toBe('true');
    expect(root().querySelectorAll('tbody tr').length).toBe(1);

    respond([anOrder({ status: 'FAILED' })]);

    expect(root().querySelector('.table-wrap')?.classList).not.toContain('is-loading');
    expect(root().querySelector('section')?.getAttribute('aria-busy')).toBe('false');
  });

  it('clears all filters with one click', async () => {
    await open('/orders?status=FAILED&search=x');
    respond([]);

    buttonByText(harness.fixture, 'Clear filters').click();
    await harness.fixture.whenStable();

    expect(currentUrl()).toBe('/orders');
    expect(listRequest().request.params.has('status')).toBeFalse();
    expect(query<HTMLSelectElement>(harness.fixture, '#filter-status').value).toBe('');
    expect(query<HTMLInputElement>(harness.fixture, '#filter-search').value).toBe('');
  });

  it('pages through the results via the URL', async () => {
    await open();
    respond([anOrder()], { totalElements: 45, totalPages: 3 });

    buttonByText(harness.fixture, 'Next').click();
    await harness.fixture.whenStable();

    expect(currentUrl()).toBe('/orders?page=2');
    expect(listRequest().request.params.get('page')).toBe('1');
  });

  it('invites a merchant without orders to create the first one', async () => {
    await open();
    respond([]);

    expect(root().textContent).toContain('No orders yet');
    expect(root().querySelector('app-empty-state a')?.getAttribute('href')).toBe('/orders/new');
    expect(root().querySelector('table')).toBeNull();
  });

  it('says nothing matched when filters are active, and offers to clear them', async () => {
    await open('/orders?status=FAILED');
    respond([]);

    expect(root().textContent).toContain('No orders match these filters');
    expect(root().textContent).not.toContain('No orders yet');

    buttonByText(harness.fixture, 'Clear filters').click();
    await harness.fixture.whenStable();
    expect(currentUrl()).toBe('/orders');
    listRequest();
  });

  it('shows an error with a retry that loads the same page again', async () => {
    await open();
    listRequest().flush(null, { status: 503, statusText: 'Unavailable' });
    harness.detectChanges();

    expect(root().querySelector('[role="alert"]')?.textContent).toContain(
      'temporarily unavailable',
    );
    expect(root().querySelector('table')).toBeNull();

    buttonByText(harness.fixture, 'Try again').click();
    harness.detectChanges();
    expect(root().querySelector('[role="alert"]')).toBeNull();
    respond();

    expect(root().querySelectorAll('tbody tr').length).toBe(1);
  });

  it('links to creating a single order and to bulk upload', async () => {
    await open();
    respond();

    const links = Array.from(root().querySelectorAll('.page__header a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(links).toEqual(['/orders/new', '/orders/bulk']);
  });
});
