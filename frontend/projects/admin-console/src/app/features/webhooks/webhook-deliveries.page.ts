import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { WebhookAdminApi } from '../../core/api/webhook-admin.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { PageResponse } from '../../core/models/page.model';
import {
  DELIVERY_STATUSES,
  DeliveryFilters,
  DeliveryStatus,
  WebhookDelivery,
} from '../../core/models/webhook.model';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { statusPresentation, StatusBadge } from '../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { DeliveryDetailDialog } from './delivery-detail-dialog';

export const DELIVERY_PAGE_SIZE = 20;

/** /admin/webhooks: every merchant webhook delivery, with details and Replay for FAILED ones. */
@Component({
  selector: 'app-webhook-deliveries-page',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    StatusBadge,
    Pagination,
    ErrorAlert,
    EmptyState,
    Spinner,
    ConfirmDialog,
    DeliveryDetailDialog,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './webhook-deliveries.page.html',
  styleUrl: './webhook-deliveries.page.css',
})
export class WebhookDeliveriesPage {
  protected readonly statuses = DELIVERY_STATUSES;

  protected readonly filters = new FormGroup({
    status: new FormControl<DeliveryStatus | ''>('', { nonNullable: true }),
    merchantId: new FormControl('', { nonNullable: true }),
    eventType: new FormControl('', { nonNullable: true }),
  });

  protected readonly result = signal<PageResponse<WebhookDelivery> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);
  /** Ids of the rows with a replay in flight. */
  protected readonly replaying = signal<ReadonlySet<string>>(new Set());

  private readonly confirm = viewChild.required(ConfirmDialog);
  private readonly detail = viewChild.required(DeliveryDetailDialog);
  private readonly api = inject(WebhookAdminApi);
  private readonly toasts = inject(ToastService);
  private applied: DeliveryFilters = {};
  private pageIndex = 0;
  private request: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
    this.load();
  }

  protected applyFilters(): void {
    const value = this.filters.getRawValue();
    this.applied = {
      status: value.status || null,
      merchantId: value.merchantId.trim() || null,
      eventType: value.eventType.trim() || null,
    };
    this.pageIndex = 0;
    this.load();
  }

  protected resetFilters(): void {
    this.filters.reset();
    this.applied = {};
    this.pageIndex = 0;
    this.load();
  }

  protected goToPage(page: number): void {
    this.pageIndex = page;
    this.load();
  }

  protected load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api
      .list({ ...this.applied, page: this.pageIndex, size: DELIVERY_PAGE_SIZE })
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.error.set(toAppError(error));
          this.loading.set(false);
        },
      });
  }

  protected openDetail(delivery: WebhookDelivery, trigger: HTMLElement | null = null): void {
    this.detail().open(delivery, trigger);
  }

  /** Mouse convenience: a click anywhere on a row opens it, except on the row's own buttons. */
  protected onRowClick(event: MouseEvent, delivery: WebhookDelivery): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest('button, a')) {
      return;
    }
    this.openDetail(delivery);
  }

  /** Asks for confirmation, then queues the delivery again. */
  protected async replay(delivery: WebhookDelivery): Promise<void> {
    if (this.replaying().has(delivery.id)) {
      return;
    }
    const confirmed = await this.confirm().ask({
      title: 'Replay this webhook delivery?',
      message:
        `SwiftDrop will send the ${delivery.eventType} event to ${delivery.url} again. ` +
        'The merchant may receive it more than once, so their endpoint should be idempotent.',
      confirmLabel: 'Replay delivery',
    });
    if (!confirmed) {
      return;
    }
    this.setReplaying(delivery.id, true);
    this.api.replay(delivery.id).subscribe({
      next: (updated) => {
        this.setReplaying(delivery.id, false);
        this.replaceRow(updated);
        this.detail().update(updated);
        this.toasts.success(
          `Replay queued. The delivery is now ${statusPresentation(updated.status).label.toLowerCase()}.`,
        );
      },
      error: (error: unknown) => {
        this.setReplaying(delivery.id, false);
        this.toasts.error(toAppError(error).message);
      },
    });
  }

  private replaceRow(updated: WebhookDelivery): void {
    this.result.update((page) =>
      page
        ? {
            ...page,
            content: page.content.map((row) => (row.id === updated.id ? updated : row)),
          }
        : page,
    );
  }

  private setReplaying(id: string, busy: boolean): void {
    this.replaying.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
