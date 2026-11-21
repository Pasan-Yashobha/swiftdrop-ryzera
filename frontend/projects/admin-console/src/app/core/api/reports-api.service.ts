import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { API_BASE_URL, APP_CONFIG } from '../config/app-config';
import {
  CourierPerformance,
  DailyDeliveryPoint,
  ReportSummary,
  SuccessRateStats,
  ZoneDeliveryStats,
} from '../models/reports.model';
import { toHttpParams } from './query-params';

/**
 * Reports API service for Admin and Dispatcher reporting endpoints.
 * Communicates with the reporting service via the API gateway.
 */
@Injectable({ providedIn: 'root' })
export class ReportsApiService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly config = inject(APP_CONFIG);

  getSummary(from?: string, to?: string): Observable<ReportSummary> {
    if (this.config.useMockApi) {
      return of({
        created: 5,
        delivered: 3,
        failed: 2,
        returned: 1,
        attemptRate: 0.5,
        finalOutcomeRate: 0.75,
        activeCouriers: 2,
        reconciliationMismatch: false,
        lastUpdatedAt: new Date().toISOString(),
      });
    }
    const params = toHttpParams({ from, to });
    return this.http.get<ReportSummary>(`${this.api}/admin/reports/summary`, { params });
  }

  getDeliveriesPerDay(
    from?: string,
    to?: string,
    zoneId?: string,
  ): Observable<DailyDeliveryPoint[]> {
    if (this.config.useMockApi) {
      return of([
        { date: '2026-11-19', created: 4, delivered: 3, failed: 1 },
        { date: '2026-11-20', created: 5, delivered: 2, failed: 2 },
        { date: '2026-11-21', created: 6, delivered: 5, failed: 1 },
      ]);
    }
    const params = toHttpParams({ from, to, zoneId });
    return this.http.get<DailyDeliveryPoint[]>(`${this.api}/admin/reports/deliveries-per-day`, {
      params,
    });
  }

  getSuccessRate(from?: string, to?: string): Observable<SuccessRateStats> {
    if (this.config.useMockApi) {
      return of({
        from: from ?? '2026-11-20',
        to: to ?? '2026-11-21',
        attemptRate: 0.5,
        finalOutcomeRate: 0.75,
      });
    }
    const params = toHttpParams({ from, to });
    return this.http.get<SuccessRateStats>(`${this.api}/admin/reports/success-rate`, { params });
  }

  getCourierPerformance(
    from?: string,
    to?: string,
    sort?: string,
  ): Observable<CourierPerformance[]> {
    if (this.config.useMockApi) {
      return of([
        {
          courierId: 'c1',
          courierName: 'Kamal Perera',
          assignedCount: 5,
          reassignedAwayCount: 0,
          deliveredCount: 4,
          failedAttempts: 1,
          totalMinutes: 180,
          timedCount: 4,
          averageDeliveryMinutes: 45.0,
        },
        {
          courierId: 'c2',
          courierName: 'Nimal Fernando',
          assignedCount: 4,
          reassignedAwayCount: 1,
          deliveredCount: 2,
          failedAttempts: 2,
          totalMinutes: 120,
          timedCount: 2,
          averageDeliveryMinutes: 60.0,
        },
      ]);
    }
    const params = toHttpParams({ from, to, sort });
    return this.http.get<CourierPerformance[]>(`${this.api}/admin/reports/courier-performance`, {
      params,
    });
  }

  getZones(from?: string, to?: string): Observable<ZoneDeliveryStats[]> {
    if (this.config.useMockApi) {
      return of([
        {
          zoneId: 'z1',
          zoneName: 'Colombo Central (Z1)',
          createdCount: 8,
          deliveredCount: 6,
          failedCount: 2,
          feeTotal: 2400,
        },
        {
          zoneId: 'z2',
          zoneName: 'Greater Colombo (Z2)',
          createdCount: 7,
          deliveredCount: 5,
          failedCount: 2,
          feeTotal: 3100,
        },
      ]);
    }
    const params = toHttpParams({ from, to });
    return this.http.get<ZoneDeliveryStats[]>(`${this.api}/admin/reports/zones`, { params });
  }

  exportCsv(from?: string, to?: string, zoneId?: string): Observable<Blob> {
    if (this.config.useMockApi) {
      return of(
        new Blob(
          [
            'created,delivered,failed,returned,attemptRate,finalOutcomeRate,activeCouriers,reconciliationMismatch,lastUpdatedAt\n' +
              '5,3,2,1,0.5,0.75,2,false,2026-11-21T00:00:00Z\n',
          ],
          { type: 'text/csv' },
        ),
      );
    }
    const params = toHttpParams({ from, to, zoneId, format: 'csv' });
    return this.http.get(`${this.api}/admin/reports/summary`, {
      params,
      responseType: 'blob',
    });
  }
}
