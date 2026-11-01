import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { OrderStats } from '../../../core/models/order.model';
import { buttonByText, element } from '../../../core/testing/dom';
import { anOrder, aPageOf, someStats } from '../../orders/testing/order-data';
import { DashboardPage } from './dashboard.page';

const STATS_URL = '/api/v1/merchants/me/orders/stats';
const LIST_URL = '/api/v1/merchants/me/orders';

describe('DashboardPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function create(): ComponentFixture<DashboardPage> {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    return fixture;
  }

  function respond(
    fixture: ComponentFixture<DashboardPage>,
    stats: OrderStats,
    orders = [anOrder(), anOrder({ id: 'o-2', trackingNo: 'SD24000124', status: 'DELIVERED' })],
  ): void {
    http.expectOne(STATS_URL).flush(stats);
    http.expectOne((request) => request.url === LIST_URL).flush(aPageOf(orders));
    fixture.detectChanges();
  }

  function text(fixture: ComponentFixture<DashboardPage>): string {
    return (element(fixture).textContent ?? '').replace(/\s+/g, ' ');
  }

  it('asks for the statistics and the five newest live orders, and shows a loading state meanwhile', () => {
    const fixture = create();

    const list = http.expectOne((request) => request.url === LIST_URL);
    expect(list.request.params.get('size')).toBe('5');
    expect(list.request.params.has('sandbox')).toBeFalse();
    expect(element(fixture).querySelector('[role="status"]')?.textContent).toContain('Loading');
    expect(element(fixture).querySelectorAll('.stat--loading').length).toBe(4);

    list.flush(aPageOf([]));
    http.expectOne(STATS_URL).flush(someStats());
  });

  it('shows the four totals', () => {
    const fixture = create();
    respond(fixture, someStats({ total: 1234, inProgress: 5, delivered: 6, failed: 1 }));

    const cards = Array.from(element(fixture).querySelectorAll('.stat')).map((card) => [
      card.querySelector('.stat__label')?.textContent,
      card.querySelector('.stat__value')?.textContent,
    ]);
    expect(cards).toEqual([
      ['Total orders', '1,234'],
      ['In progress', '5'],
      ['Delivered', '6'],
      ['Failed', '1'],
    ]);
  });

  it('shows the seven-day chart and the by-status breakdown with links to the filtered list', () => {
    const fixture = create();
    respond(fixture, someStats());

    expect(element(fixture).querySelectorAll('app-orders-chart rect.chart__bar').length).toBe(7);
    const entries = Array.from(element(fixture).querySelectorAll('.breakdown__link'));
    expect(
      entries.map((entry) => [
        entry.querySelector('app-status-badge')?.textContent?.trim(),
        entry.querySelector('.breakdown__count')?.textContent,
      ]),
    ).toEqual([
      ['●Created', '3'],
      ['●In transit', '2'],
      ['✓Delivered', '6'],
      ['✕Failed', '1'],
    ]);
    expect(entries[3].getAttribute('href')).toBe('/orders?status=FAILED');
    const bar = element(fixture).querySelector<HTMLElement>('.breakdown__bar');
    expect(bar?.style.width).toBe('25%');
  });

  it('lists the recent orders with links to their details', () => {
    const fixture = create();
    respond(fixture, someStats());

    const items = element(fixture).querySelectorAll('.recent__item');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('SD24000123');
    expect(items[0].textContent).toContain('Nimali Perera');
    expect(items[1].textContent).toContain('Delivered');
    expect(items[0].querySelector('a')?.getAttribute('href')).toBe(
      '/orders/a1b2c3d4-0000-4000-8000-000000000001',
    );
  });

  it('links to creating an order and to bulk upload', () => {
    const fixture = create();
    respond(fixture, someStats());

    const links = Array.from(element(fixture).querySelectorAll('.page__header a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(links).toContain('/orders/new');
    expect(links).toContain('/orders/bulk');
  });

  it('says the figures are live-only and links to the sandbox orders', () => {
    const fixture = create();
    respond(fixture, someStats());

    expect(text(fixture)).toContain('These figures cover live orders only.');
    expect(
      Array.from(element(fixture).querySelectorAll('a'))
        .find((a) => a.textContent?.includes('sandbox orders'))
        ?.getAttribute('href'),
    ).toBe('/orders?sandbox=sandbox');
  });

  it('guides a brand-new merchant instead of showing empty charts', () => {
    const fixture = create();
    respond(
      fixture,
      someStats({ total: 0, byStatus: {}, inProgress: 0, delivered: 0, failed: 0 }),
      [],
    );

    const content = text(fixture);
    expect(content).toContain('You have no live orders yet');
    expect(content).toContain('Create an API key');
    expect(content).toContain('Send a test order');
    const hrefs = Array.from(element(fixture).querySelectorAll('.steps a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toEqual([
      '/developers/keys',
      '/orders/new?sandbox=true',
      '/orders/new',
      '/developers/webhooks',
    ]);
    expect(element(fixture).querySelector('app-orders-chart')).toBeNull();
    expect(element(fixture).querySelector('.stat')).toBeNull();
  });

  it('shows the error with a retry that loads the data again', () => {
    const fixture = create();
    http.expectOne((request) => request.url === LIST_URL).flush(aPageOf([]));
    http.expectOne(STATS_URL).flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(element(fixture).querySelector('[role="alert"]')?.textContent).toContain(
      'temporarily unavailable',
    );
    expect(element(fixture).querySelector('.stat')).toBeNull();

    buttonByText(fixture, 'Try again').click();
    fixture.detectChanges();
    expect(element(fixture).querySelector('[role="alert"]')).toBeNull();
    respond(fixture, someStats());

    expect(element(fixture).querySelector('.stat__value')?.textContent).toBe('12');
  });
});
