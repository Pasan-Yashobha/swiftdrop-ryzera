import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ParcelApi } from '../../core/api/parcel.api';
import { CustomerParcelDetail } from '../../core/models/parcel.model';

@Component({
  selector: 'app-parcel-details-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <div class="cluster" style="margin-bottom: var(--space-1);">
            <a routerLink="/history" class="btn btn-secondary btn-sm">&larr; Back to History</a>
            @if (parcel(); as p) {
              <span class="muted small mono">ID: {{ p.id }}</span>
            }
          </div>
          <h1>
            @if (parcel(); as p) {
              Shipment {{ p.trackingNumber }}
            } @else {
              Parcel Details
            }
          </h1>
        </div>

        @if (parcel(); as p) {
          <div class="cluster">
            <span class="badge" [class]="'badge--' + p.status.toLowerCase()" style="font-size: var(--text-md); padding: var(--space-2) var(--space-4);">
              {{ formatStatus(p.status) }}
            </span>
            <a [routerLink]="['/track']" [queryParams]="{ trackingNo: p.trackingNumber }" class="btn btn-primary btn-sm">
              Track Publicly &rarr;
            </a>
          </div>
        }
      </header>

      <!-- Loading State -->
      @if (loading()) {
        <section class="card stack" role="status" aria-live="polite" style="text-align: center; padding-block: var(--space-8);">
          <p class="muted">Loading parcel details from Parcel Service...</p>
        </section>
      }

      <!-- Unauthorized Alert -->
      @if (isUnauthorized()) {
        <section class="card" role="alert" style="border-left: 4px solid var(--tone-warning-fg); background: var(--tone-warning-bg); color: var(--tone-warning-fg);">
          <h3>Authentication Required</h3>
          <p style="margin-top: var(--space-1);">
            You must be signed in with a valid customer account to view full parcel details.
          </p>
        </section>
      }

      <!-- Not Found Alert -->
      @if (isNotFound()) {
        <section class="card stack" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg);">
          <h3>Parcel Not Found</h3>
          <p>We could not find a parcel corresponding to ID "{{ parcelId() }}".</p>
          <div>
            <a routerLink="/history" class="btn btn-secondary btn-sm">&larr; Return to History</a>
          </div>
        </section>
      }

      <!-- General Error Alert -->
      @if (error(); as err) {
        <section class="card stack" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg);">
          <p><strong>Error:</strong> {{ err }}</p>
          <div>
            <button type="button" class="btn btn-secondary btn-sm" (click)="loadParcel(parcelId())">
              Retry
            </button>
          </div>
        </section>
      }

      <!-- Parcel Details Content -->
      @if (!loading() && parcel(); as p) {
        <!-- Failure Alert if Status is FAILED -->
        @if (p.failureReason) {
          <div class="card" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg);">
            <strong>Delivery Issue:</strong> {{ p.failureReason }}
          </div>
        }

        <div class="grid-2">
          <!-- Addresses & Specifications -->
          <section class="card stack" aria-labelledby="route-heading">
            <h2 id="route-heading">Addresses & Specifications</h2>

            <div class="stack" style="gap: var(--space-3);">
              <div>
                <span class="muted small">Pickup Location</span>
                <p><strong>{{ p.pickup.addressLine }}</strong></p>
                <p class="muted small">{{ p.pickup.city }}{{ p.pickup.postalCode ? ', ' + p.pickup.postalCode : '' }}</p>
              </div>

              <hr style="border: 0; border-top: 1px solid var(--color-border); margin: 0;" />

              <div>
                <span class="muted small">Recipient & Dropoff Location</span>
                <p><strong>{{ p.recipient.name }}</strong> ({{ p.recipient.phone }})</p>
                @if (p.recipient.email) {
                  <p class="muted small">{{ p.recipient.email }}</p>
                }
                <p style="margin-top: var(--space-1);">
                  <strong>{{ p.dropoff.addressLine }}</strong>, {{ p.dropoff.city }}{{ p.dropoff.postalCode ? ', ' + p.dropoff.postalCode : '' }}
                </p>
              </div>

              <hr style="border: 0; border-top: 1px solid var(--color-border); margin: 0;" />

              <div class="grid-3">
                <div>
                  <span class="muted small">Weight</span>
                  <p><strong>{{ p.weightKg }} kg</strong></p>
                </div>
                <div>
                  <span class="muted small">Package Size</span>
                  <p><strong>{{ p.size }}</strong></p>
                </div>
                <div>
                  <span class="muted small">Delivery Priority</span>
                  <p><strong>{{ p.deliveryType }}</strong></p>
                </div>
              </div>

              @if (p.description) {
                <div>
                  <span class="muted small">Package Description</span>
                  <p>{{ p.description }}</p>
                </div>
              }

              @if (p.notes) {
                <div>
                  <span class="muted small">Delivery Instructions</span>
                  <p>{{ p.notes }}</p>
                </div>
              }

              <div class="grid-2">
                <div>
                  <span class="muted small">Created</span>
                  <p class="mono small">{{ formatDateTime(p.createdAt) }}</p>
                </div>
                <div>
                  <span class="muted small">Last Updated</span>
                  <p class="mono small">{{ formatDateTime(p.updatedAt) }}</p>
                </div>
              </div>

              @if (p.deliveredAt) {
                <div class="card" style="background: var(--tone-success-bg); color: var(--tone-success-fg); padding: var(--space-2) var(--space-3);">
                  <strong>Delivered At:</strong> {{ formatDateTime(p.deliveredAt) }}
                </div>
              }
            </div>
          </section>

          <!-- Pricing Snapshot & Courier Card -->
          <div class="stack">
            @if (p.pricing; as pr) {
              <section class="card stack" aria-labelledby="pricing-heading">
                <h2 id="pricing-heading">Pricing Snapshot</h2>
                <div class="stack" style="gap: var(--space-2); border-bottom: 1px dashed var(--color-border); padding-bottom: var(--space-3);">
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Base Amount:</span>
                    <span>{{ pr.currency }} {{ pr.baseAmount.toFixed(2) }}</span>
                  </div>
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Weight Charge:</span>
                    <span>{{ pr.currency }} {{ pr.weightAmount.toFixed(2) }}</span>
                  </div>
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Size Allowance:</span>
                    <span>{{ pr.currency }} {{ pr.sizeAmount.toFixed(2) }}</span>
                  </div>
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Express Priority:</span>
                    <span>{{ pr.currency }} {{ pr.expressAmount.toFixed(2) }}</span>
                  </div>
                  @if (pr.discountAmount > 0) {
                    <div class="cluster" style="justify-content: space-between; color: var(--tone-success-fg);">
                      <span>Discount:</span>
                      <span>-{{ pr.currency }} {{ pr.discountAmount.toFixed(2) }}</span>
                    </div>
                  }
                </div>

                <div class="cluster" style="justify-content: space-between; font-size: var(--text-lg); font-weight: 700;">
                  <span>Total Accepted Fee:</span>
                  <span style="color: var(--color-primary);">{{ pr.currency }} {{ pr.totalFee.toFixed(2) }}</span>
                </div>

                <p class="muted small mono" style="margin-top: var(--space-1);">
                  Accepted Quote ID: {{ pr.quoteId }}
                </p>
              </section>
            }

            <section class="card stack" aria-labelledby="courier-heading">
              <h2 id="courier-heading">Assigned Courier</h2>
              @if (p.courierId) {
                <div class="cluster" style="justify-content: space-between;">
                  <div>
                    <p><strong>SwiftDrop Courier Partner</strong></p>
                    <p class="muted small mono">ID: {{ p.courierId }}</p>
                  </div>
                  <span class="badge badge--assigned">Assigned</span>
                </div>
              } @else {
                <p class="muted">No courier assigned yet. Pending dispatcher allocation.</p>
              }
            </section>
          </div>
        </div>

        <!-- Status Audit Trail Table -->
        <section class="card stack" aria-labelledby="audit-heading">
          <h2 id="audit-heading">Status History ({{ p.history.length }})</h2>
          @if (p.history.length === 0) {
            <p class="muted">No status transitions recorded yet.</p>
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">Seq #</th>
                    <th scope="col">From Status</th>
                    <th scope="col">To Status</th>
                    <th scope="col">Timestamp</th>
                    <th scope="col">Actor Role</th>
                    <th scope="col">Changed By</th>
                    <th scope="col">Notes</th>
                  </tr>
                </thead>
                <tbody>
                  @for (entry of p.history; track entry.sequenceNumber) {
                    <tr>
                      <td class="mono small">{{ entry.sequenceNumber }}</td>
                      <td>
                        @if (entry.fromStatus) {
                          <span class="badge" [class]="'badge--' + entry.fromStatus.toLowerCase()">
                            {{ formatStatus(entry.fromStatus) }}
                          </span>
                        } @else {
                          <span class="muted">&mdash;</span>
                        }
                      </td>
                      <td>
                        <span class="badge" [class]="'badge--' + entry.toStatus.toLowerCase()">
                          {{ formatStatus(entry.toStatus) }}
                        </span>
                      </td>
                      <td class="muted small mono">{{ formatDateTime(entry.occurredAt) }}</td>
                      <td>{{ entry.actorRole || '&mdash;' }}</td>
                      <td class="mono small">{{ entry.changedBy || '&mdash;' }}</td>
                      <td class="small">{{ entry.notes || '&mdash;' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class ParcelDetailsPage implements OnInit {
  readonly id = input<string>();

  private readonly route = inject(ActivatedRoute);
  private readonly parcelApi = inject(ParcelApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly parcelId = signal<string>('');
  protected readonly parcel = signal<CustomerParcelDetail | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly isUnauthorized = signal(false);
  protected readonly isNotFound = signal(false);

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const pid = params.get('id') || this.id();
      if (pid) {
        this.parcelId.set(pid);
        this.loadParcel(pid);
      }
    });
  }

  protected loadParcel(id: string): void {
    if (!id) return;

    this.loading.set(true);
    this.error.set(null);
    this.isUnauthorized.set(false);
    this.isNotFound.set(false);

    this.parcelApi
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.parcel.set(data);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 401 || err.status === 403) {
              this.isUnauthorized.set(true);
              return;
            }
            if (err.status === 404) {
              this.isNotFound.set(true);
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
