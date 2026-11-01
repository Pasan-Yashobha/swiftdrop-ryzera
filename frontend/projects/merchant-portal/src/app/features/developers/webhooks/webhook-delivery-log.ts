import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { WebhookApi } from '../../../core/api/webhook.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { WebhookDeliveryPage } from '../../../core/models/webhook.model';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { Pagination } from '../../../shared/ui/pagination/pagination';
import { Skeleton } from '../../../shared/ui/spinner/skeleton';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { DeveloperError } from '../shared/developer-error';

/** Recent webhook deliveries with their outcome. The delivery log is a separate service and can be unavailable. */
@Component({
  selector: 'app-webhook-delivery-log',
  imports: [DatePipe, EmptyState, Pagination, Skeleton, StatusBadge, DeveloperError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './webhook-delivery-log.html',
  styleUrl: '../shared/responsive-table.css',
})
export class WebhookDeliveryLog {
  protected readonly deliveries = signal<WebhookDeliveryPage | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);

  private readonly api = inject(WebhookApi);
  private pageIndex = 0;

  constructor() {
    this.load(0);
  }

  protected refresh(): void {
    this.load(this.pageIndex);
  }

  protected load(page: number): void {
    this.pageIndex = page;
    this.loading.set(true);
    this.error.set(null);
    this.api.deliveries(page).subscribe({
      next: (deliveries) => {
        this.deliveries.set(deliveries);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }
}
