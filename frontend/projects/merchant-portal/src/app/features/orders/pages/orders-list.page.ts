import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  EMPTY,
  Subject,
  catchError,
  combineLatest,
  debounceTime,
  map,
  merge,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { OrderApi } from '../../../core/api/order.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { Order, SandboxFilter } from '../../../core/models/order.model';
import { PageResponse } from '../../../core/models/page.model';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../../shared/ui/error-alert/error-alert';
import { Pagination } from '../../../shared/ui/pagination/pagination';
import { Skeleton } from '../../../shared/ui/spinner/skeleton';
import { StatusBadge, statusPresentation } from '../../../shared/ui/status-badge/status-badge';
import {
  FILTERABLE_STATUSES,
  NO_FILTERS,
  OrderFilters,
  OrdersListState,
  SANDBOX_FILTERS,
  hasActiveFilters,
  paramsFromState,
  sameFilters,
  stateFromParams,
  toOrderQuery,
} from '../util/order-list-query';

/** Wait for the typing to pause before searching. */
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * The merchant's orders. The filters and the page live in the URL query parameters, so a filtered list can be
 * shared or bookmarked and the back button steps through earlier filters.
 */
@Component({
  selector: 'app-orders-list-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MoneyPipe,
    RelativeTimePipe,
    EmptyState,
    ErrorAlert,
    Pagination,
    Skeleton,
    StatusBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './orders-list.page.html',
  styleUrl: './orders-list.page.css',
})
export class OrdersListPage {
  protected readonly statusOptions = FILTERABLE_STATUSES.map((status) => ({
    value: status,
    label: statusPresentation(status).label,
  }));
  protected readonly sandboxOptions = SANDBOX_FILTERS;

  protected readonly filterForm = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    status: new FormControl<OrderFilters['status']>('', { nonNullable: true }),
    sandbox: new FormControl<SandboxFilter>('live', { nonNullable: true }),
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });

  protected readonly state = signal<OrdersListState>({ filters: NO_FILTERS, page: 0 });
  protected readonly results = signal<PageResponse<Order> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly filtered = computed(() => hasActiveFilters(this.state().filters));

  private readonly api = inject(OrderApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly retried = new Subject<void>();
  /** The filters the URL was last set to from this page; tells our own navigations from back-button ones. */
  private applied: OrderFilters = NO_FILTERS;

  constructor() {
    const controls = this.filterForm.controls;
    merge(
      controls.search.valueChanges.pipe(debounceTime(SEARCH_DEBOUNCE_MS)),
      controls.status.valueChanges,
      controls.sandbox.valueChanges,
      controls.from.valueChanges,
      controls.to.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.applyFilters());

    combineLatest([
      this.route.queryParamMap.pipe(map(stateFromParams)),
      this.retried.pipe(startWith(undefined)),
    ])
      .pipe(
        tap(([state]) => this.show(state)),
        switchMap(([state]) =>
          this.api.list(toOrderQuery(state)).pipe(
            tap({
              next: (page) => {
                this.results.set(page);
                this.loading.set(false);
              },
              error: (error: unknown) => {
                this.error.set(toAppError(error));
                this.results.set(null);
                this.loading.set(false);
              },
            }),
            catchError(() => EMPTY),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  protected applyFilters(): void {
    const raw = this.filterForm.getRawValue();
    const filters: OrderFilters = { ...raw, search: raw.search.trim() };
    if (!sameFilters(filters, this.applied)) {
      this.applied = filters;
      this.navigate({ filters, page: 0 });
    }
  }

  protected clearFilters(): void {
    this.filterForm.reset(NO_FILTERS, { emitEvent: false });
    this.applyFilters();
  }

  protected goToPage(page: number): void {
    this.navigate({ filters: this.state().filters, page });
  }

  protected retry(): void {
    this.retried.next();
  }

  /** Reflects the URL in the page: the form follows the URL when it was changed from outside (back button). */
  private show(state: OrdersListState): void {
    this.state.set(state);
    this.error.set(null);
    this.loading.set(true);
    if (!sameFilters(state.filters, this.applied)) {
      this.applied = state.filters;
      this.filterForm.reset(state.filters, { emitEvent: false });
    }
  }

  private navigate(state: OrdersListState): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: paramsFromState(state) });
  }
}
