import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { OrderApi } from '../../../core/api/order.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { ORDER_STATUSES, Order, OrderStats } from '../../../core/models/order.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { ErrorAlert } from '../../../shared/ui/error-alert/error-alert';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { OrdersChart } from '../components/orders-chart';

const RECENT_ORDER_COUNT = 5;

/** Landing page: live order totals, the last seven days, where orders stand, and the newest orders. */
@Component({
  selector: 'app-dashboard-page',
  imports: [DecimalPipe, RouterLink, RelativeTimePipe, ErrorAlert, StatusBadge, OrdersChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.css',
})
export class DashboardPage {
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly stats = signal<OrderStats | null>(null);
  protected readonly recentOrders = signal<readonly Order[]>([]);

  /** Orders per status in lifecycle order, with their share of all orders; empty statuses are left out. */
  protected readonly breakdown = computed(() => {
    const stats = this.stats();
    if (!stats || stats.total === 0) {
      return [];
    }
    return ORDER_STATUSES.map((status) => ({ status, count: stats.byStatus[status] ?? 0 }))
      .filter((entry) => entry.count > 0)
      .map((entry) => ({ ...entry, share: (entry.count / stats.total) * 100 }));
  });

  /** A merchant who has not created a live order yet gets next steps instead of empty charts. */
  protected readonly isNewMerchant = computed(
    () => this.stats()?.total === 0 && this.recentOrders().length === 0,
  );

  private readonly api = inject(OrderApi);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      stats: this.api.stats(),
      recent: this.api.list({ size: RECENT_ORDER_COUNT }),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ stats, recent }) => {
          this.stats.set(stats);
          this.recentOrders.set(recent.content);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.error.set(toAppError(error));
          this.loading.set(false);
        },
      });
  }
}
