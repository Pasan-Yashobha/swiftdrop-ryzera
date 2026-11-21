import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { errorInterceptor } from '../../core/http/error.interceptor';
import {
  CourierPerformance,
  DailyDeliveryPoint,
  ReportSummary,
  ZoneDeliveryStats,
} from '../../core/models/reports.model';
import { buttonByText, element } from '../../core/testing/dom';
import { ReportsPage } from './reports.page';

describe('ReportsPage', () => {
  let fixture: ComponentFixture<ReportsPage>;
  let component: ReportsPage;
  let http: HttpTestingController;

  const mockSummary: ReportSummary = {
    created: 25,
    delivered: 20,
    failed: 5,
    returned: 2,
    attemptRate: 0.8,
    finalOutcomeRate: 0.909,
    activeCouriers: 4,
    reconciliationMismatch: false,
    lastUpdatedAt: '2026-11-21T18:00:00Z',
  };

  const mockDaily: DailyDeliveryPoint[] = [
    { date: '2026-11-20', created: 12, delivered: 10, failed: 2 },
    { date: '2026-11-21', created: 13, delivered: 10, failed: 3 },
  ];

  const mockCouriers: CourierPerformance[] = [
    {
      courierId: 'c1',
      courierName: 'Kamal Perera',
      assignedCount: 15,
      reassignedAwayCount: 1,
      deliveredCount: 12,
      failedAttempts: 2,
      totalMinutes: 480,
      timedCount: 12,
      averageDeliveryMinutes: 40.0,
    },
    {
      courierId: 'c2',
      courierName: 'Nimal Fernando',
      assignedCount: 10,
      reassignedAwayCount: 0,
      deliveredCount: 8,
      failedAttempts: 3,
      totalMinutes: 400,
      timedCount: 8,
      averageDeliveryMinutes: 50.0,
    },
  ];

  const mockZones: ZoneDeliveryStats[] = [
    {
      zoneId: 'z1',
      zoneName: 'Colombo Central',
      createdCount: 15,
      deliveredCount: 12,
      failedCount: 3,
      feeTotal: 6000,
    },
    {
      zoneId: 'z2',
      zoneName: 'Greater Colombo',
      createdCount: 10,
      deliveredCount: 8,
      failedCount: 2,
      feeTotal: 4000,
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ReportsPage],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportsPage);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    http.verify();
  });

  function flushRequests(
    summary = mockSummary,
    daily = mockDaily,
    couriers = mockCouriers,
    zones = mockZones,
  ) {
    http.expectOne((r) => r.url.endsWith('/admin/reports/summary')).flush(summary);
    http.expectOne((r) => r.url.endsWith('/admin/reports/deliveries-per-day')).flush(daily);
    http.expectOne((r) => r.url.endsWith('/admin/reports/courier-performance')).flush(couriers);
    http.expectOne((r) => r.url.endsWith('/admin/reports/zones')).flush(zones);
  }

  it('shows loading skeleton while data is in flight', () => {
    fixture.detectChanges();

    const skeleton = element(fixture).querySelector('.skeleton-grid');
    expect(skeleton).not.toBeNull();
    expect(skeleton?.getAttribute('aria-busy')).toBe('true');

    flushRequests();
    fixture.detectChanges();

    expect(element(fixture).querySelector('.skeleton-grid')).toBeNull();
  });

  it('renders KPI cards with attempt rate, outcome rate, and last updated timestamp', () => {
    fixture.detectChanges();
    flushRequests();
    fixture.detectChanges();

    const el = element(fixture);
    expect(el.textContent).toContain('Reports Dashboard');
    expect(el.textContent).toContain('25'); // created
    expect(el.textContent).toContain('20'); // delivered
    expect(el.textContent).toContain('80.0%'); // attempt rate
    expect(el.textContent).toContain('90.9%'); // outcome rate
    expect(el.textContent).toContain('4'); // active couriers
    expect(el.textContent).toContain('Kamal Perera');
    expect(el.textContent).toContain('Colombo Central');
  });

  it('shows empty state when no delivery records exist for the window', () => {
    fixture.detectChanges();
    flushRequests({
      ...mockSummary,
      created: 0,
      delivered: 0,
      failed: 0,
      returned: 0,
      attemptRate: null,
      finalOutcomeRate: null,
      activeCouriers: 0,
    });
    fixture.detectChanges();

    const empty = element(fixture).querySelector('app-empty-state');
    expect(empty).not.toBeNull();
    expect(element(fixture).textContent).toContain('No delivery data recorded');
  });

  it('displays error alert on server failure and retries on demand', () => {
    fixture.detectChanges();

    http.expectOne((r) => r.url.endsWith('/admin/reports/deliveries-per-day')).flush([]);
    http.expectOne((r) => r.url.endsWith('/admin/reports/courier-performance')).flush([]);
    http.expectOne((r) => r.url.endsWith('/admin/reports/zones')).flush([]);
    http.expectOne((r) => r.url.endsWith('/admin/reports/summary')).flush(
      { code: 'INTERNAL_ERROR', message: 'Database query failed' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    const errorAlert = element(fixture).querySelector('app-error-alert');
    expect(errorAlert).not.toBeNull();

    // Click retry
    const retryBtn = buttonByText(fixture, 'Try again');
    retryBtn.click();
    fixture.detectChanges();

    flushRequests();
    fixture.detectChanges();

    expect(element(fixture).querySelector('app-error-alert')).toBeNull();
    expect(element(fixture).textContent).toContain('25');
  });

  it('triggers CSV download with date range naming when Export CSV is clicked', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
    spyOn(URL, 'revokeObjectURL');
    const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');

    fixture.detectChanges();
    flushRequests();
    fixture.detectChanges();

    const exportBtn = buttonByText(fixture, '↓ Export CSV');
    exportBtn.click();
    fixture.detectChanges();

    const csvReq = http.expectOne(
      (r) => r.url.endsWith('/admin/reports/summary') && r.params.get('format') === 'csv',
    );
    expect(csvReq.request.responseType).toBe('blob');
    const fakeBlob = new Blob(['created,delivered\n25,20'], { type: 'text/csv' });
    csvReq.flush(fakeBlob);
    fixture.detectChanges();

    const clickedLink = clickSpy.calls.mostRecent().object as HTMLAnchorElement;
    expect(clickedLink.download).toMatch(/^reports-.*\.csv$/);
  });

  it('applies presets like Today and reloads the data', () => {
    fixture.detectChanges();
    flushRequests();
    fixture.detectChanges();

    const todayBtn = buttonByText(fixture, 'Today');
    todayBtn.click();
    fixture.detectChanges();

    // Should issue new requests
    flushRequests();
    fixture.detectChanges();
    expect(component.preset()).toBe('today');
  });

  it('sorts couriers by column when clicking header', () => {
    fixture.detectChanges();
    flushRequests();
    fixture.detectChanges();

    expect(component.sortedCouriers()[0].courierName).toBe('Kamal Perera');

    // Click Courier Name header to sort by name
    const th = element(fixture).querySelector('.sortable-th') as HTMLElement;
    th.click();
    fixture.detectChanges();

    // Descending or ascending
    expect(component.sortField()).toBe('courierName');
  });

  it('displays reconciliation mismatch alert when flag is set', () => {
    fixture.detectChanges();
    flushRequests({
      ...mockSummary,
      reconciliationMismatch: true,
    });
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('Nightly reconciliation flagged a discrepancy');
  });
});
