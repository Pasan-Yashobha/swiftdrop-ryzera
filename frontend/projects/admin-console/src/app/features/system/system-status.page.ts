import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { SystemAdminApi } from '../../core/api/system-admin.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { ServiceHealth } from '../../core/models/system.model';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { StatusBadge } from '../../shared/ui/status-badge/status-badge';

/** The page checks again this often (the gateway caches its answer for 10 seconds). */
export const REFRESH_INTERVAL_MS = 15_000;

/** Local development addresses of the observability tools; they are links only. */
export const GRAFANA_URL = 'http://localhost:3001';
export const PROMETHEUS_URL = 'http://localhost:9090';

/** /admin/system: is every SwiftDrop service up? One call to the gateway checks all of them. */
@Component({
  selector: 'app-system-status-page',
  imports: [DatePipe, StatusBadge, ErrorAlert, EmptyState, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './system-status.page.html',
  styleUrl: './system-status.page.css',
})
export class SystemStatusPage {
  protected readonly grafanaUrl = GRAFANA_URL;
  protected readonly prometheusUrl = PROMETHEUS_URL;

  protected readonly services = signal<ServiceHealth[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);
  protected readonly checkedAt = signal<Date | null>(null);

  protected readonly total = computed(() => this.services()?.length ?? 0);
  protected readonly upCount = computed(
    () => (this.services() ?? []).filter((service) => service.status === 'UP').length,
  );
  protected readonly allUp = computed(() => this.total() > 0 && this.upCount() === this.total());

  private readonly api = inject(SystemAdminApi);
  private request: Subscription | null = null;

  constructor() {
    const timer = setInterval(() => this.load(), REFRESH_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      this.request?.unsubscribe();
    });
    this.load();
  }

  protected load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api.health().subscribe({
      next: (services) => {
        this.services.set(services);
        this.checkedAt.set(new Date());
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }
}
