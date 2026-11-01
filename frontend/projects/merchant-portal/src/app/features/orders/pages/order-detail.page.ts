import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, combineLatest, startWith, switchMap, tap } from 'rxjs';
import { OrderApi } from '../../../core/api/order.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { OrderDetail } from '../../../core/models/order.model';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { CopyButton } from '../../../shared/ui/copy-button/copy-button';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../../shared/ui/error-alert/error-alert';
import { Skeleton } from '../../../shared/ui/spinner/skeleton';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { OrderTimeline } from '../components/order-timeline';
import { ORDER_SOURCE_LABELS, PARCEL_SIZE_LABELS } from '../util/order-labels';

/** One order with its addresses, parcel, fee and status history. The order id comes from the route. */
@Component({
  selector: 'app-order-detail-page',
  imports: [
    DatePipe,
    RouterLink,
    CopyButton,
    EmptyState,
    ErrorAlert,
    MoneyPipe,
    OrderTimeline,
    RelativeTimePipe,
    Skeleton,
    StatusBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-detail.page.html',
  styleUrl: './order-detail.page.css',
})
export class OrderDetailPage {
  readonly id = input.required<string>();

  protected readonly sizeLabels = PARCEL_SIZE_LABELS;
  protected readonly sourceLabels = ORDER_SOURCE_LABELS;

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly error = signal<AppError | null>(null);
  protected readonly notFound = computed(() => this.error()?.status === 404);

  private readonly api = inject(OrderApi);
  private readonly retried = new Subject<void>();

  constructor() {
    combineLatest([toObservable(this.id), this.retried.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.order.set(null);
          this.error.set(null);
        }),
        switchMap(([id]) =>
          this.api.get(id).pipe(
            tap({
              next: (order) => this.order.set(order),
              error: (error: unknown) => this.error.set(toAppError(error)),
            }),
            catchError(() => EMPTY),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  protected retry(): void {
    this.retried.next();
  }
}
