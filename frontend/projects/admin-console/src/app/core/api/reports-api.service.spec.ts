import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../config/app-config';
import { ReportsApiService } from './reports-api.service';

describe('ReportsApiService', () => {
  let service: ReportsApiService;
  let http: HttpTestingController;

  describe('with real API (useMockApi: false)', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: APP_CONFIG,
            useValue: { production: false, apiBase: '/api/v1', devLogin: true, useMockApi: false },
          },
        ],
      });
      service = TestBed.inject(ReportsApiService);
      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      http.verify();
    });

    it('fetches summary metrics', () => {
      service.getSummary('2026-11-20', '2026-11-21').subscribe((res) => {
        expect(res.created).toBe(10);
        expect(res.attemptRate).toBe(0.8);
      });

      const req = http.expectOne('/api/v1/admin/reports/summary?from=2026-11-20&to=2026-11-21');
      expect(req.request.method).toBe('GET');
      req.flush({
        created: 10,
        delivered: 8,
        failed: 2,
        returned: 1,
        attemptRate: 0.8,
        finalOutcomeRate: 0.89,
        activeCouriers: 5,
        reconciliationMismatch: false,
        lastUpdatedAt: '2026-11-21T10:00:00Z',
      });
    });

    it('fetches daily deliveries points', () => {
      service.getDeliveriesPerDay('2026-11-20', '2026-11-21', 'z1').subscribe((res) => {
        expect(res.length).toBe(1);
        expect(res[0].date).toBe('2026-11-20');
      });

      const req = http.expectOne(
        '/api/v1/admin/reports/deliveries-per-day?from=2026-11-20&to=2026-11-21&zoneId=z1',
      );
      expect(req.request.method).toBe('GET');
      req.flush([{ date: '2026-11-20', created: 5, delivered: 4, failed: 1 }]);
    });

    it('fetches success rate statistics', () => {
      service.getSuccessRate('2026-11-20', '2026-11-21').subscribe((res) => {
        expect(res.attemptRate).toBe(0.75);
      });

      const req = http.expectOne('/api/v1/admin/reports/success-rate?from=2026-11-20&to=2026-11-21');
      expect(req.request.method).toBe('GET');
      req.flush({ from: '2026-11-20', to: '2026-11-21', attemptRate: 0.75, finalOutcomeRate: 0.85 });
    });

    it('fetches courier performance with sorting', () => {
      service.getCourierPerformance('2026-11-20', '2026-11-21', 'deliveredCount,desc').subscribe((res) => {
        expect(res.length).toBe(1);
        expect(res[0].courierName).toBe('Kamal Perera');
      });

      const req = http.expectOne(
        (r) =>
          r.url === '/api/v1/admin/reports/courier-performance' &&
          r.params.get('sort') === 'deliveredCount,desc',
      );
      expect(req.request.method).toBe('GET');
      req.flush([
        {
          courierId: 'c1',
          courierName: 'Kamal Perera',
          assignedCount: 4,
          reassignedAwayCount: 0,
          deliveredCount: 4,
          failedAttempts: 0,
          totalMinutes: 120,
          timedCount: 4,
          averageDeliveryMinutes: 30.0,
        },
      ]);
    });

    it('fetches zone statistics', () => {
      service.getZones('2026-11-20', '2026-11-21').subscribe((res) => {
        expect(res.length).toBe(1);
        expect(res[0].zoneName).toBe('Colombo Central');
      });

      const req = http.expectOne('/api/v1/admin/reports/zones?from=2026-11-20&to=2026-11-21');
      expect(req.request.method).toBe('GET');
      req.flush([
        {
          zoneId: 'z1',
          zoneName: 'Colombo Central',
          createdCount: 10,
          deliveredCount: 8,
          failedCount: 2,
          feeTotal: 4000,
        },
      ]);
    });

    it('downloads CSV report as a Blob with auth token passing through HttpClient', () => {
      service.exportCsv('2026-11-20', '2026-11-21').subscribe((blob) => {
        expect(blob instanceof Blob).toBeTrue();
      });

      const req = http.expectOne(
        '/api/v1/admin/reports/summary?from=2026-11-20&to=2026-11-21&format=csv',
      );
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['col1,col2\nval1,val2'], { type: 'text/csv' }));
    });
  });

  describe('with mock API (useMockApi: true)', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: APP_CONFIG,
            useValue: { production: false, apiBase: '/api/v1', devLogin: true, useMockApi: true },
          },
        ],
      });
      service = TestBed.inject(ReportsApiService);
      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      http.verify();
    });

    it('returns mock summary without making HTTP requests', () => {
      service.getSummary().subscribe((res) => {
        expect(res.created).toBe(5);
        expect(res.delivered).toBe(3);
      });
      http.expectNone('/api/v1/admin/reports/summary');
    });

    it('returns mock CSV blob without making HTTP requests', () => {
      service.exportCsv().subscribe((blob) => {
        expect(blob).toBeTruthy();
        expect(blob.type).toBe('text/csv');
      });
      http.expectNone('/api/v1/admin/reports/summary?format=csv');
    });
  });
});
