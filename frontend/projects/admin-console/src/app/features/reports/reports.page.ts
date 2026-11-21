import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, interval } from 'rxjs';
import { ReportsApiService } from '../../core/api/reports-api.service';
import { AppError, toAppError } from '../../core/http/app-error';
import {
  CourierPerformance,
  DailyDeliveryPoint,
  ReportFilter,
  ReportPreset,
  ReportSummary,
  ZoneDeliveryStats,
} from '../../core/models/reports.model';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { downloadBlob } from '../../shared/util/download-file';

@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe, ErrorAlert, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reports.page.html',
  styleUrls: ['../route-board.css', './reports.page.css'],
})
export class ReportsPage implements OnInit {
  private readonly reportsApi = inject(ReportsApiService);
  private readonly destroyRef = inject(DestroyRef);

  // Filter signals
  readonly preset = signal<ReportPreset>('7d');
  readonly from = signal<string>('');
  readonly to = signal<string>('');
  readonly selectedZone = signal<string>('');
  readonly autoRefresh = signal<boolean>(true);

  // Courier table sorting
  readonly sortField = signal<keyof CourierPerformance>('deliveredCount');
  readonly sortDirection = signal<'asc' | 'desc'>('desc');

  // State signals
  readonly loading = signal<boolean>(false);
  readonly exporting = signal<boolean>(false);
  readonly error = signal<AppError | null>(null);

  // Data signals
  readonly summary = signal<ReportSummary | null>(null);
  readonly dailyPoints = signal<DailyDeliveryPoint[]>([]);
  readonly couriers = signal<CourierPerformance[]>([]);
  readonly zones = signal<ZoneDeliveryStats[]>([]);

  // Computed properties
  readonly isEmpty = computed(() => {
    const s = this.summary();
    return s !== null && s.created === 0 && s.delivered === 0 && s.failed === 0;
  });

  readonly sortedCouriers = computed(() => {
    const list = [...this.couriers()];
    const field = this.sortField();
    const dir = this.sortDirection() === 'asc' ? 1 : -1;

    return list.sort((a, b) => {
      const valA = a[field];
      const valB = b[field];

      if (valA == null && valB == null) return 0;
      if (valA == null) return 1;
      if (valB == null) return -1;

      if (typeof valA === 'string' && typeof valB === 'string') {
        return valA.localeCompare(valB) * dir;
      }
      return ((valA as number) - (valB as number)) * dir;
    });
  });

  // SVG Chart Computations
  readonly lineChart = computed(() => {
    const points = this.dailyPoints();
    if (points.length === 0) return null;

    const width = 600;
    const height = 240;
    const padX = 45;
    const padY = 30;
    const plotW = width - padX * 2;
    const plotH = height - padY * 2;

    const maxVal = Math.max(...points.map((p) => Math.max(p.created, p.delivered, 1)), 5);
    const stepX = points.length > 1 ? plotW / (points.length - 1) : plotW / 2;

    const createdPoints = points.map((p, i) => {
      const x = padX + i * stepX;
      const y = padY + plotH - (p.created / maxVal) * plotH;
      return { x, y, val: p.created, date: p.date };
    });

    const deliveredPoints = points.map((p, i) => {
      const x = padX + i * stepX;
      const y = padY + plotH - (p.delivered / maxVal) * plotH;
      return { x, y, val: p.delivered, date: p.date };
    });

    const createdPath = createdPoints.map((p) => `${p.x},${p.y}`).join(' ');
    const deliveredPath = deliveredPoints.map((p) => `${p.x},${p.y}`).join(' ');

    const gridLines = [0, 0.5, 1].map((ratio) => ({
      y: padY + plotH - ratio * plotH,
      label: Math.round(ratio * maxVal),
    }));

    return {
      width,
      height,
      points,
      createdPoints,
      deliveredPoints,
      createdPath,
      deliveredPath,
      gridLines,
      padY,
      plotH,
    };
  });

  readonly stackedBarChart = computed(() => {
    const points = this.dailyPoints();
    if (points.length === 0) return null;

    const width = 600;
    const height = 240;
    const padX = 45;
    const padY = 30;
    const plotW = width - padX * 2;
    const plotH = height - padY * 2;

    const maxTotal = Math.max(...points.map((p) => p.delivered + p.failed), 5);
    const stepX = plotW / points.length;
    const barW = Math.max(Math.min(stepX * 0.6, 32), 8);

    const bars = points.map((p, i) => {
      const x = padX + i * stepX + (stepX - barW) / 2;
      const delH = (p.delivered / maxTotal) * plotH;
      const failH = (p.failed / maxTotal) * plotH;
      const delY = padY + plotH - delH;
      const failY = delY - failH;

      return {
        x,
        barW,
        delivered: p.delivered,
        failed: p.failed,
        delH,
        delY,
        failH,
        failY,
        date: p.date,
      };
    });

    const gridLines = [0, 0.5, 1].map((ratio) => ({
      y: padY + plotH - ratio * plotH,
      label: Math.round(ratio * maxTotal),
    }));

    return { width, height, bars, gridLines, padY, plotH };
  });

  readonly zoneChart = computed(() => {
    const zList = this.zones();
    if (zList.length === 0) return null;

    const width = 600;
    const height = 240;
    const padX = 50;
    const padY = 30;
    const plotW = width - padX * 2;
    const plotH = height - padY * 2;

    const maxVol = Math.max(...zList.map((z) => Math.max(z.createdCount, z.deliveredCount)), 5);
    const stepX = plotW / zList.length;
    const barW = Math.max(Math.min(stepX * 0.35, 24), 6);

    const bars = zList.map((z, i) => {
      const groupX = padX + i * stepX + (stepX - barW * 2 - 4) / 2;
      const createdH = (z.createdCount / maxVol) * plotH;
      const createdY = padY + plotH - createdH;
      const delH = (z.deliveredCount / maxVol) * plotH;
      const delY = padY + plotH - delH;

      return {
        zoneName: z.zoneName,
        createdCount: z.createdCount,
        deliveredCount: z.deliveredCount,
        createdX: groupX,
        createdY,
        createdH,
        delX: groupX + barW + 4,
        delY,
        delH,
        barW,
      };
    });

    const gridLines = [0, 0.5, 1].map((ratio) => ({
      y: padY + plotH - ratio * plotH,
      label: Math.round(ratio * maxVol),
    }));

    return { width, height, bars, gridLines, padY, plotH };
  });

  ngOnInit(): void {
    this.applyPreset('7d');
    this.setupAutoRefresh();
  }

  applyPreset(preset: ReportPreset): void {
    this.preset.set(preset);
    const today = new Date();
    const toStr = this.toDateString(today);

    if (preset === 'today') {
      this.from.set(toStr);
      this.to.set(toStr);
    } else if (preset === '7d') {
      const fromDate = new Date(today.getTime() - 6 * 86400000);
      this.from.set(this.toDateString(fromDate));
      this.to.set(toStr);
    } else if (preset === '30d') {
      const fromDate = new Date(today.getTime() - 29 * 86400000);
      this.from.set(this.toDateString(fromDate));
      this.to.set(toStr);
    }

    this.loadData();
  }

  onCustomDateChange(): void {
    if (this.from() && this.to()) {
      this.preset.set('custom');
      this.loadData();
    }
  }

  onZoneChange(zoneId: string): void {
    this.selectedZone.set(zoneId);
    this.loadData();
  }

  toggleAutoRefresh(): void {
    this.autoRefresh.update((v) => !v);
  }

  setSorting(field: keyof CourierPerformance): void {
    if (this.sortField() === field) {
      this.sortDirection.update((dir) => (dir === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortField.set(field);
      this.sortDirection.set('desc');
    }
  }

  loadData(silent = false): void {
    if (this.loading()) return;
    if (!silent) {
      this.loading.set(true);
    }
    this.error.set(null);

    const fromVal = this.from() || undefined;
    const toVal = this.to() || undefined;
    const zoneVal = this.selectedZone() || undefined;

    forkJoin({
      summary: this.reportsApi.getSummary(fromVal, toVal),
      daily: this.reportsApi.getDeliveriesPerDay(fromVal, toVal, zoneVal),
      couriers: this.reportsApi.getCourierPerformance(fromVal, toVal),
      zones: this.reportsApi.getZones(fromVal, toVal),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (data) => {
          this.summary.set(data.summary);
          this.dailyPoints.set(data.daily);
          this.couriers.set(data.couriers);
          this.zones.set(data.zones);
        },
        error: (err: unknown) => {
          this.error.set(toAppError(err));
        },
      });
  }

  exportCsv(): void {
    this.exporting.set(true);
    const fromVal = this.from() || 'all';
    const toVal = this.to() || 'all';
    const zoneVal = this.selectedZone() || undefined;

    this.reportsApi
      .exportCsv(this.from() || undefined, this.to() || undefined, zoneVal)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.exporting.set(false)),
      )
      .subscribe({
        next: (blob) => {
          downloadBlob(blob, `reports-${fromVal}_${toVal}.csv`);
        },
        error: (err: unknown) => {
          this.error.set(toAppError(err));
        },
      });
  }

  private setupAutoRefresh(): void {
    interval(60000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.autoRefresh() && !this.loading()) {
          this.loadData(true);
        }
      });
  }

  private toDateString(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
