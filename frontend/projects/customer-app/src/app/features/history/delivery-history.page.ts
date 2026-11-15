import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ParcelApi } from '../../core/api/parcel.api';
import { AuthTokenService } from '../../core/auth/auth-token.service';
import { CustomerParcelSummary, ParcelStatus } from '../../core/models/parcel.model';

@Component({
  selector: 'app-delivery-history-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1>Delivery History</h1>
          <p class="page__lead">Review your past parcel shipments, live delivery progress, and digital receipts.</p>
        </div>
        <div class="cluster">
          <a routerLink="/book" class="btn btn-primary btn-sm">+ Book New Delivery</a>
        </div>
      </header>

      <!-- Filter and Search Toolbar -->
      <section class="card stack" aria-label="Filters">
        <div class="grid-2" style="align-items: center; gap: var(--space-3);">
          <form class="form-group" style="margin: 0;" (submit)="onSearchSubmit($event)">
            <label for="history-search" class="visually-hidden">Search Deliveries</label>
            <div class="cluster" style="gap: var(--space-2);">
              <input
                id="history-search"
                type="search"
                class="form-input"
                placeholder="Search by tracking number or recipient..."
                [value]="searchQuery()"
                (input)="onSearchInput($event)"
              />
              <button type="submit" class="btn btn-secondary btn-sm" [disabled]="loading()">
                Search
              </button>
            </div>
          </form>

          <div class="cluster" style="justify-content: flex-end; flex-wrap: wrap;">
            <button
              type="button"
              class="btn btn-sm"
              [class.btn-primary]="selectedStatus() === 'ALL'"
              [class.btn-secondary]="selectedStatus() !== 'ALL'"
              (click)="onFilterChange('ALL')"
            >
              All
            </button>
            <button
              type="button"
              class="btn btn-sm"
              [class.btn-primary]="selectedStatus() === 'IN_TRANSIT'"
              [class.btn-secondary]="selectedStatus() !== 'IN_TRANSIT'"
              (click)="onFilterChange('IN_TRANSIT')"
            >
              In Transit
            </button>
            <button
              type="button"
              class="btn btn-sm"
              [class.btn-primary]="selectedStatus() === 'DELIVERED'"
              [class.btn-secondary]="selectedStatus() !== 'DELIVERED'"
              (click)="onFilterChange('DELIVERED')"
            >
              Delivered
            </button>
            <button
              type="button"
              class="btn btn-sm"
              [class.btn-primary]="selectedStatus() === 'CANCELLED'"
              [class.btn-secondary]="selectedStatus() !== 'CANCELLED'"
              (click)="onFilterChange('CANCELLED')"
            >
              Cancelled
            </button>
          </div>
        </div>
      </section>

      <!-- Unauthorized Alert (JWT Session Requirement) -->
      @if (isUnauthorized()) {
        <section class="card" role="alert" style="border-left: 4px solid var(--tone-warning-fg); background: var(--tone-warning-bg); color: var(--tone-warning-fg);">
          <h3>Authentication Required</h3>
          <p style="margin-top: var(--space-1);">
            Parcel Service requires an authenticated customer JWT token to list personal delivery history.
            Once customer sign-in is complete, your deliveries will load automatically.
          </p>
        </section>
      }

      <!-- General Error Alert -->
      @if (error(); as err) {
        <section class="card" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg);">
          <p><strong>Error:</strong> {{ err }}</p>
          <button type="button" class="btn btn-secondary btn-sm" (click)="loadDeliveries()" style="margin-top: var(--space-2);">
            Retry
          </button>
        </section>
      }

      <!-- Loading State -->
      @if (loading()) {
        <section class="card stack" role="status" aria-live="polite" style="text-align: center; padding-block: var(--space-8);">
          <p class="muted">Loading delivery records from Parcel Service...</p>
        </section>
      }

      <!-- History Content -->
      @if (!loading() && !isUnauthorized() && !error()) {
        <section class="card stack" aria-label="Shipment list">
          @if (parcels().length === 0) {
            <div style="text-align: center; padding-block: var(--space-8);">
              <p class="muted">No delivery records found matching your query.</p>
              <div class="cluster" style="justify-content: center; margin-top: var(--space-3);">
                <a routerLink="/book" class="btn btn-primary btn-sm">+ Book Your First Delivery</a>
              </div>
            </div>
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">Tracking No</th>
                    <th scope="col">Booked At</th>
                    <th scope="col">Recipient</th>
                    <th scope="col">Route</th>
                    <th scope="col">Weight / Size</th>
                    <th scope="col">Fee</th>
                    <th scope="col">Status</th>
                    <th scope="col">Action</th>
                  </tr>
                </thead>
                <tbody>
                  @for (parcel of parcels(); track parcel.id) {
                    <tr>
                      <td class="mono">
                        <strong>{{ parcel.trackingNumber }}</strong>
                      </td>
                      <td class="muted small mono">{{ formatDateTime(parcel.createdAt) }}</td>
                      <td>{{ parcel.recipientName }}</td>
                      <td>{{ parcel.pickupCity }} &rarr; {{ parcel.dropoffCity }}</td>
                      <td>{{ parcel.weightKg }} kg ({{ parcel.size }})</td>
                      <td>{{ parcel.currency }} {{ parcel.totalFee.toFixed(2) }}</td>
                      <td>
                        <span class="badge" [class]="'badge--' + parcel.status.toLowerCase()">
                          {{ formatStatus(parcel.status) }}
                        </span>
                      </td>
                      <td>
                        <a [routerLink]="['/parcels', parcel.id]" class="btn btn-secondary btn-sm">
                          Details
                        </a>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            <!-- Pagination Bar -->
            <div class="cluster" style="justify-content: space-between; border-top: 1px solid var(--color-border); padding-top: var(--space-3); margin-top: var(--space-3);">
              <span class="muted small">
                Showing {{ parcels().length }} of {{ totalElements() }} deliveries (Page {{ currentPage() + 1 }} of {{ totalPages() || 1 }})
              </span>
              <div class="cluster" style="gap: var(--space-2);">
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  [disabled]="currentPage() === 0 || loading()"
                  (click)="changePage(currentPage() - 1)"
                >
                  &larr; Previous
                </button>
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  [disabled]="currentPage() >= totalPages() - 1 || loading()"
                  (click)="changePage(currentPage() + 1)"
                >
                  Next &rarr;
                </button>
              </div>
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class DeliveryHistoryPage implements OnInit {
  private readonly parcelApi = inject(ParcelApi);
  private readonly auth = inject(AuthTokenService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly parcels = signal<CustomerParcelSummary[]>([]);
  protected readonly searchQuery = signal('');
  protected readonly selectedStatus = signal<ParcelStatus | 'ALL'>('ALL');
  protected readonly currentPage = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly isUnauthorized = signal(false);

  ngOnInit(): void {
    this.loadDeliveries();
  }

  protected onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.searchQuery.set(input.value);
  }

  protected onSearchSubmit(event: Event): void {
    event.preventDefault();
    this.currentPage.set(0);
    this.loadDeliveries();
  }

  protected onFilterChange(status: ParcelStatus | 'ALL'): void {
    this.selectedStatus.set(status);
    this.currentPage.set(0);
    this.loadDeliveries();
  }

  protected changePage(page: number): void {
    if (page >= 0 && (this.totalPages() === 0 || page < this.totalPages())) {
      this.currentPage.set(page);
      this.loadDeliveries();
    }
  }

  protected loadDeliveries(): void {
    this.loading.set(true);
    this.error.set(null);
    this.isUnauthorized.set(false);

    const statusParam =
      this.selectedStatus() !== 'ALL' ? (this.selectedStatus() as ParcelStatus) : undefined;

    this.parcelApi
      .list({
        status: statusParam,
        search: this.searchQuery().trim() || undefined,
        page: this.currentPage(),
        size: 10,
        sort: 'createdAt,desc',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.parcels.set(page.content);
          this.currentPage.set(page.page);
          this.totalPages.set(page.totalPages);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 401 || err.status === 403) {
              this.isUnauthorized.set(true);
              return;
            }
            if (err.error?.message) {
              this.error.set(err.error.message);
              return;
            }
          }
          this.error.set('Failed to connect to the Parcel Service. Please check if the backend is running.');
        },
      });
  }

  protected formatStatus(status: string): string {
    return status
      .split('_')
      .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
      .join(' ');
  }

  protected formatDateTime(instantString: string): string {
    try {
      const date = new Date(instantString);
      return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return instantString;
    }
  }
}
