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
import { CustomerParcelSummary } from '../../core/models/parcel.model';

@Component({
  selector: 'app-dashboard-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1>Customer Dashboard</h1>
          <p class="page__lead">Welcome to SwiftDrop. Book, manage, and track your parcel deliveries in real time.</p>
        </div>
        <div class="cluster">
          <a routerLink="/book" class="btn btn-primary btn-lg">+ Book Delivery</a>
        </div>
      </header>

      <!-- Quick Actions -->
      <section aria-labelledby="quick-actions-heading" class="stack">
        <h2 id="quick-actions-heading" class="visually-hidden">Quick Actions</h2>
        <div class="grid-3">
          <article class="card card--interactive stack">
            <h3>Send a Package</h3>
            <p class="muted">Book an instant city or islandwide courier pickup with upfront pricing.</p>
            <div style="margin-top: auto;">
              <a routerLink="/book" class="btn btn-primary btn-sm">Book Delivery &rarr;</a>
            </div>
          </article>

          <article class="card card--interactive stack">
            <h3>Track Shipment</h3>
            <p class="muted">Check real-time status and delivery milestones for any tracking number.</p>
            <div style="margin-top: auto;">
              <a routerLink="/track" class="btn btn-secondary btn-sm">Track Parcel &rarr;</a>
            </div>
          </article>

          <article class="card card--interactive stack">
            <h3>Delivery History</h3>
            <p class="muted">View past shipments, status audit trails, and manage active deliveries.</p>
            <div style="margin-top: auto;">
              <a routerLink="/history" class="btn btn-secondary btn-sm">View History &rarr;</a>
            </div>
          </article>
        </div>
      </section>

      <!-- Recent Shipments Live Section -->
      <section aria-labelledby="active-shipments-heading" class="card stack">
        <div class="page__header">
          <h2 id="active-shipments-heading">Recent Shipments</h2>
          <a routerLink="/history" class="btn btn-outline btn-sm">View all deliveries</a>
        </div>

        @if (loading()) {
          <div style="text-align: center; padding-block: var(--space-6);">
            <p class="muted">Loading recent shipments from Parcel Service...</p>
          </div>
        } @else if (isUnauthorized()) {
          <div style="padding-block: var(--space-4);">
            <p class="muted">
              Sign in with your customer account to view your recent shipments here, or use
              <a routerLink="/track" style="color: var(--color-primary); font-weight: 600;">Track Parcel</a>
              to lookup any parcel by tracking number without signing in.
            </p>
          </div>
        } @else if (error(); as err) {
          <div style="padding-block: var(--space-4); color: var(--tone-danger-fg);">
            <p><strong>Note:</strong> {{ err }}</p>
          </div>
        } @else if (recentParcels().length === 0) {
          <div style="text-align: center; padding-block: var(--space-6);">
            <p class="muted">No recent shipments found.</p>
            <div class="cluster" style="justify-content: center; margin-top: var(--space-2);">
              <a routerLink="/book" class="btn btn-primary btn-sm">+ Book Your First Delivery</a>
            </div>
          </div>
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th scope="col">Tracking No</th>
                  <th scope="col">Recipient</th>
                  <th scope="col">Destination</th>
                  <th scope="col">Status</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                @for (parcel of recentParcels(); track parcel.id) {
                  <tr>
                    <td class="mono"><strong>{{ parcel.trackingNumber }}</strong></td>
                    <td>{{ parcel.recipientName }}</td>
                    <td>{{ parcel.dropoffCity }}</td>
                    <td>
                      <span class="badge" [class]="'badge--' + parcel.status.toLowerCase()">
                        {{ formatStatus(parcel.status) }}
                      </span>
                    </td>
                    <td>
                      <a [routerLink]="['/parcels', parcel.id]" class="btn btn-secondary btn-sm">Details</a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </div>
  `,
})
export class DashboardPage implements OnInit {
  private readonly parcelApi = inject(ParcelApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly recentParcels = signal<CustomerParcelSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly isUnauthorized = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.parcelApi
      .list({ size: 5, sort: 'createdAt,desc' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.recentParcels.set(page.content);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 401 || err.status === 403) {
              this.isUnauthorized.set(true);
              return;
            }
          }
          this.error.set('Could not connect to Parcel Service to retrieve recent shipments.');
        },
      });
  }

  protected formatStatus(status: string): string {
    return status
      .split('_')
      .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
      .join(' ');
  }
}
