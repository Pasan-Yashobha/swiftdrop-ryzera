import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { OrderDetail } from '../../../core/models/order.model';
import { buttonByText, element } from '../../../core/testing/dom';
import { anOrderDetail } from '../testing/order-data';
import { OrderDetailPage } from './order-detail.page';

const orderUrl = (id: string) => `/api/v1/merchants/me/orders/${id}`;

describe('OrderDetailPage', () => {
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

  function create(id = 'order-1'): ComponentFixture<OrderDetailPage> {
    const fixture = TestBed.createComponent(OrderDetailPage);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    return fixture;
  }

  function show(order: OrderDetail = anOrderDetail()): ComponentFixture<OrderDetailPage> {
    const fixture = create();
    http.expectOne(orderUrl('order-1')).flush(order);
    fixture.detectChanges();
    return fixture;
  }

  /** The text of the label/value pairs of the first `<dl>` of the section with the given heading. */
  function pairs(fixture: ComponentFixture<OrderDetailPage>, heading: string): Map<string, string> {
    const section = Array.from(element(fixture).querySelectorAll('section')).find(
      (candidate) => candidate.querySelector('h2')?.textContent === heading,
    );
    const terms = Array.from(section?.querySelectorAll('dt') ?? []);
    return new Map(
      terms.map((term) => [
        term.textContent ?? '',
        (term.nextElementSibling?.textContent ?? '').replace(/\s+/g, ' ').trim(),
      ]),
    );
  }

  it('loads the order named by the route and shows a loading state meanwhile', () => {
    const fixture = create('abc');

    expect(element(fixture).querySelector('app-skeleton')?.textContent).toContain('Loading order');
    http.expectOne(orderUrl('abc')).flush(anOrderDetail());
    fixture.detectChanges();

    expect(element(fixture).querySelector('app-skeleton')).toBeNull();
  });

  it('loads the new order when the route id changes', () => {
    const fixture = create('first');
    http.expectOne(orderUrl('first')).flush(anOrderDetail());

    fixture.componentRef.setInput('id', 'second');
    fixture.detectChanges();

    http.expectOne(orderUrl('second')).flush(anOrderDetail({ trackingNo: 'SD24000999' }));
    fixture.detectChanges();
    expect(element(fixture).querySelector('h1')?.textContent).toContain('SD24000999');
  });

  it('shows the tracking number and status in the heading', () => {
    const fixture = show(anOrderDetail({ status: 'IN_TRANSIT' }));

    const heading = element(fixture).querySelector('h1');
    expect(heading?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Order SD24000123');
    expect(element(fixture).querySelector('.page__header app-status-badge')?.textContent).toContain(
      'In transit',
    );
    expect(element(fixture).querySelector('app-status-badge[status="SANDBOX"]')).toBeNull();
  });

  it('shows the summary with the fee, the source and the times', () => {
    const fixture = show();

    const summary = pairs(fixture, 'Summary');
    expect(summary.get('Tracking number')).toContain('SD24000123');
    expect(summary.get('Your reference')).toContain('ORDER-1001');
    expect(summary.get('Environment')).toBe('Live');
    expect(summary.get('Created through')).toBe('Merchant portal');
    expect(summary.get('Delivery fee')).toBe('LKR 450.00');
    expect(summary.get('Created')).toMatch(/Oct 30, 2026.*\(.+\)/);
    expect(summary.get('Last updated')).toMatch(/Oct 30, 2026.*\(.+\)/);
  });

  it('links to the public tracking page and offers copy buttons for the identifiers', () => {
    const fixture = show();

    const link = element(fixture).querySelector<HTMLAnchorElement>('section dd a');
    expect(link?.href).toBe('http://localhost:4200/track/SD24000123');
    expect(link?.target).toBe('_blank');
    expect(link?.rel).toContain('noopener');
    expect(
      Array.from(element(fixture).querySelectorAll('app-copy-button')).map((b) =>
        b.textContent?.trim(),
      ),
    ).toEqual(['Copy number', 'Copy link', 'Copy reference']);
  });

  it('shows recipient, both addresses, parcel and notes', () => {
    const fixture = show();
    const text = (element(fixture).textContent ?? '').replace(/\s+/g, ' ');

    expect(pairs(fixture, 'Recipient')).toEqual(
      new Map([
        ['Name', 'Nimali Perera'],
        ['Phone', '+94771234567'],
      ]),
    );
    expect(pairs(fixture, 'Parcel')).toEqual(
      new Map([
        ['Weight', '2.5 kg'],
        ['Size', 'Small'],
        ['Description', 'Phone case'],
        ['Notes', 'Call on arrival'],
      ]),
    );
    expect(text).toContain('12 Galle Rd Colombo 00300');
    expect(text).toContain('45 Temple St Kandy 20000');
  });

  it('says so when optional details are missing', () => {
    const fixture = show(
      anOrderDetail({
        trackingNo: null,
        trackingUrl: null,
        externalRef: null,
        fee: null,
        notes: null,
        parcel: { weightKg: 1, size: 'LARGE', description: null },
        pickup: { addressLine: '1 A Rd', city: 'Colombo', postalCode: null },
      }),
    );

    expect(pairs(fixture, 'Summary').get('Tracking number')).toBe('Not assigned yet');
    expect(pairs(fixture, 'Summary').get('Delivery fee')).toBe('Not priced yet');
    expect(pairs(fixture, 'Summary').has('Your reference')).toBeFalse();
    expect(pairs(fixture, 'Summary').has('Tracking link')).toBeFalse();
    expect(pairs(fixture, 'Parcel').get('Description')).toBe('Not provided');
    expect(pairs(fixture, 'Parcel').get('Notes')).toBe('None');
    expect(pairs(fixture, 'Parcel').get('Size')).toBe('Large');
    expect(element(fixture).querySelector('h1')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Order',
    );
  });

  it('marks a sandbox order', () => {
    const fixture = show(anOrderDetail({ sandbox: true }));

    expect(element(fixture).querySelector('.page__header')?.textContent).toContain('Sandbox');
    expect(pairs(fixture, 'Summary').get('Environment')).toBe('Sandbox (simulated)');
  });

  it('shows the failure reason prominently', () => {
    const fixture = show(
      anOrderDetail({ status: 'FAILED', failureReason: 'Recipient was not available' }),
    );

    const alert = element(fixture).querySelector('.alert--danger');
    expect(alert?.textContent).toContain('Why this delivery failed');
    expect(alert?.textContent).toContain('Recipient was not available');
  });

  it('shows no failure notice for orders that have not failed', () => {
    expect(element(show()).querySelector('.alert')).toBeNull();
  });

  it('shows the status history as a timeline, oldest first, ending with the current status', () => {
    const fixture = show(
      anOrderDetail({
        status: 'IN_TRANSIT',
        history: [
          {
            fromStatus: 'CREATED',
            toStatus: 'IN_TRANSIT',
            occurredAt: '2026-10-31T10:00:00Z',
            note: null,
          },
          { fromStatus: null, toStatus: 'CREATED', occurredAt: '2026-10-30T08:00:00Z', note: null },
        ],
      }),
    );

    const steps = Array.from(element(fixture).querySelectorAll('app-order-timeline li'));
    expect(steps.length).toBe(2);
    expect(steps[0].textContent).toContain('Created');
    expect(steps[1].textContent).toContain('In transit');
    expect(steps[1].textContent).toContain('Current status');
  });

  it('links back to the orders list', () => {
    const fixture = show();

    expect(element(fixture).querySelector('.back-link')?.getAttribute('href')).toBe('/orders');
  });

  it('shows a not-found state when the order does not exist or belongs to someone else', () => {
    const fixture = create();

    http
      .expectOne(orderUrl('order-1'))
      .flush(
        { code: 'NOT_FOUND', message: 'Order not found' },
        { status: 404, statusText: 'Not Found' },
      );
    fixture.detectChanges();

    const text = element(fixture).textContent ?? '';
    expect(text).toContain('Order not found');
    expect(text).toContain('belongs to another account');
    expect(element(fixture).querySelector('[role="alert"]')).toBeNull();
    expect(element(fixture).querySelector('app-empty-state a')?.getAttribute('href')).toBe(
      '/orders',
    );
  });

  it('shows other errors with a retry', () => {
    const fixture = create();
    http.expectOne(orderUrl('order-1')).flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(element(fixture).querySelector('[role="alert"]')?.textContent).toContain(
      'temporarily unavailable',
    );

    buttonByText(fixture, 'Try again').click();
    fixture.detectChanges();
    http.expectOne(orderUrl('order-1')).flush(anOrderDetail());
    fixture.detectChanges();

    expect(element(fixture).querySelector('[role="alert"]')).toBeNull();
    expect(element(fixture).querySelector('h1')?.textContent).toContain('SD24000123');
  });
});
