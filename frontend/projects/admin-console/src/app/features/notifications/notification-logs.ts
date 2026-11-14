import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { NotificationAdminApi } from '../../core/api/notification-admin.api';
import { AppError, toAppError } from '../../core/http/app-error';
import {
  NOTIFICATION_CHANNELS,
  NOTIFICATION_STATUSES,
  NotificationChannel,
  NotificationLog,
  NotificationLogFilters,
  NotificationStatus,
} from '../../core/models/notification.model';
import { PageResponse } from '../../core/models/page.model';
import { dateRangeValidator, endOfDayUtc, startOfDayUtc } from '../../shared/util/date-range';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { StatusBadge } from '../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../shared/ui/toast/toast.service';

export const NOTIFICATION_PAGE_SIZE = 20;

/** The notification log: filters, a paged table and Resend for FAILED rows. */
@Component({
  selector: 'app-notification-logs',
  imports: [ReactiveFormsModule, DatePipe, StatusBadge, Pagination, ErrorAlert, EmptyState, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notification-logs.html',
})
export class NotificationLogs {
  protected readonly statuses = NOTIFICATION_STATUSES;
  protected readonly channels = NOTIFICATION_CHANNELS;

  protected readonly filters = new FormGroup(
    {
      status: new FormControl<NotificationStatus | ''>('', { nonNullable: true }),
      channel: new FormControl<NotificationChannel | ''>('', { nonNullable: true }),
      eventType: new FormControl('', { nonNullable: true }),
      from: new FormControl('', { nonNullable: true }),
      to: new FormControl('', { nonNullable: true }),
    },
    { validators: dateRangeValidator() },
  );

  protected readonly result = signal<PageResponse<NotificationLog> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);
  /** Ids of the rows with a resend in flight. */
  protected readonly resending = signal<ReadonlySet<string>>(new Set());

  private readonly api = inject(NotificationAdminApi);
  private readonly toasts = inject(ToastService);
  private applied: NotificationLogFilters = {};
  private pageIndex = 0;
  private request: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
    this.load();
  }

  protected applyFilters(): void {
    if (this.filters.invalid) {
      this.filters.markAllAsTouched();
      return;
    }
    const value = this.filters.getRawValue();
    this.applied = {
      status: value.status || null,
      channel: value.channel || null,
      eventType: value.eventType.trim() || null,
      from: startOfDayUtc(value.from),
      to: endOfDayUtc(value.to),
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
      .list({ ...this.applied, page: this.pageIndex, size: NOTIFICATION_PAGE_SIZE })
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

  protected resend(log: NotificationLog): void {
    if (this.resending().has(log.id)) {
      return;
    }
    this.setResending(log.id, true);
    this.api.resend(log.id).subscribe({
      next: (updated) => {
        this.setResending(log.id, false);
        this.replaceRow(updated);
        if (updated.status === 'SENT') {
          this.toasts.success(`Notification to ${updated.recipient} was resent.`);
        } else {
          this.toasts.info(`Resend finished with status ${updated.status}.`);
        }
      },
      error: (error: unknown) => {
        this.setResending(log.id, false);
        this.toasts.error(toAppError(error).message);
      },
    });
  }

  private replaceRow(updated: NotificationLog): void {
    this.result.update((page) =>
      page
        ? {
            ...page,
            content: page.content.map((row) => (row.id === updated.id ? updated : row)),
          }
        : page,
    );
  }

  private setResending(id: string, busy: boolean): void {
    this.resending.update((current) => {
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
