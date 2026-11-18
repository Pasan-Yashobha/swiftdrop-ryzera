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
import { ActivatedRoute } from '@angular/router';
import { TrackingApi } from '../../core/api/tracking.api';
import { PublicTrackingResponse } from '../../core/models/tracking.model';

@Component({
  selector: 'app-track-parcel-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1>Track Your Parcel</h1>
          <p class="page__lead">Enter your SwiftDrop tracking number to check real-time status and delivery milestones.</p>
        </div>
      </header>

      <!-- Tracking Input Form -->
      <section class="card stack" aria-labelledby="track-search-heading">
        <h2 id="track-search-heading" class="visually-hidden">Enter Tracking Number</h2>
        <form class="cluster" style="align-items: stretch; gap: var(--space-2);" (submit)="onTrackSubmit($event)">
          <div class="form-group" style="flex: 1; margin: 0;">
            <label for="tracking-number-input" class="visually-hidden">Tracking Number</label>
            <input
              id="tracking-number-input"
              type="text"
              class="form-input form-input-lg mono"
              placeholder="e.g. SD260001001"
              [value]="trackingNumber()"
              (input)="onInputChange($event)"
              [disabled]="loading()"
              style="text-transform: uppercase;"
              autocomplete="off"
            />
          </div>
          <button type="submit" class="btn btn-primary btn-lg" [disabled]="loading() || !trackingNumber().trim()">
            @if (loading()) {
              Tracking...
            } @else {
              Track Shipment
            }
          </button>
        </form>

        <div class="cluster" style="font-size: var(--text-sm); gap: var(--space-2);">
          <span class="muted">Try sample:</span>
          <button
            type="button"
            class="btn btn-link btn-sm mono"
            (click)="quickTrack('SD260001001')"
            style="padding: 0; text-decoration: underline; background: none; border: none; cursor: pointer; color: var(--color-primary);"
          >
            SD260001001
          </button>
        </div>
      </section>

      <!-- Loading State -->
      @if (loading()) {
        <section class="card stack" role="status" aria-live="polite" style="text-align: center; padding-block: var(--space-7);">
          <p class="muted">Fetching real-time tracking details from Parcel Service...</p>
        </section>
      }

      <!-- Error State -->
      @if (error(); as err) {
        <section class="card" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg);">
          <p><strong>Tracking Error:</strong> {{ err }}</p>
        </section>
      }

      <!-- Tracking Results -->
      @if (!loading() && result(); as res) {
        <section class="card stack" aria-labelledby="status-summary-heading">
          <div class="page__header" style="border-bottom: 1px solid var(--color-border); padding-bottom: var(--space-4);">
            <div>
              <span class="muted small">Tracking Number:</span>
              <h2 id="status-summary-heading" class="mono" style="margin-top: var(--space-1);">{{ res.trackingNumber }}</h2>
            </div>
            <div class="cluster">
              <span class="badge" [class]="'badge--' + res.status.toLowerCase()" style="font-size: var(--text-sm); padding: var(--space-1) var(--space-3);">
                {{ formatStatus(res.status) }}
              </span>
            </div>
          </div>

          <div class="grid-3" style="margin-block: var(--space-3);">
            <div>
              <span class="muted small">Origin City</span>
              <p><strong>{{ res.pickupCity }}</strong></p>
            </div>
            <div>
              <span class="muted small">Destination City</span>
              <p><strong>{{ res.dropoffCity }}</strong></p>
            </div>
            <div>
              <span class="muted small">Booking Date</span>
              <p class="mono small">{{ formatDateTime(res.createdAt) }}</p>
            </div>
          </div>

          @if (res.deliveredAt) {
            <div class="card" style="background: var(--tone-success-bg); color: var(--tone-success-fg); padding: var(--space-3);">
              <strong>Delivered:</strong> Package successfully delivered at {{ formatDateTime(res.deliveredAt) }}
            </div>
          }

          <!-- Milestone Progress Timeline -->
          <div class="stack" style="margin-top: var(--space-4);">
            <h3>Milestone History ({{ res.milestones.length }})</h3>
            @if (res.milestones.length === 0) {
              <p class="muted">Parcel created. Awaiting courier pickup and dispatch milestones.</p>
            } @else {
              <ol class="timeline-list" style="list-style: none; padding: 0; margin: 0;">
                @for (milestone of res.milestones; track milestone.occurredAt) {
                  <li
                    class="timeline-item"
                    style="display: flex; gap: var(--space-4); margin-bottom: var(--space-4); position: relative;"
                  >
                    <div
                      class="timeline-bullet"
                      [style.background]="milestone.status === res.status ? 'var(--color-primary)' : 'var(--color-border)'"
                      style="width: 16px; height: 16px; border-radius: 50%; flex-shrink: 0; margin-top: 4px;"
                    ></div>
                    <div class="timeline-content" style="flex: 1;">
                      <div class="cluster" style="justify-content: space-between;">
                        <span class="badge" [class]="'badge--' + milestone.status.toLowerCase()">
                          {{ formatStatus(milestone.status) }}
                        </span>
                        <span class="muted small mono">{{ formatDateTime(milestone.occurredAt) }}</span>
                      </div>
                      @if (milestone.notes) {
                        <p class="muted small" style="margin-top: var(--space-1);">{{ milestone.notes }}</p>
                      }
                    </div>
                  </li>
                }
              </ol>
            }
          </div>
        </section>
      }
    </div>
  `,
})
export class TrackParcelPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly trackingApi = inject(TrackingApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly trackingNumber = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<PublicTrackingResponse | null>(null);

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const qTracking = params.get('trackingNo');
      if (qTracking) {
        this.trackingNumber.set(qTracking.trim().toUpperCase());
        this.executeTrack(qTracking.trim().toUpperCase());
      }
    });
  }

  protected onInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.trackingNumber.set(input.value.trim().toUpperCase());
  }

  protected quickTrack(sampleNo: string): void {
    this.trackingNumber.set(sampleNo);
    this.executeTrack(sampleNo);
  }

  protected onTrackSubmit(event?: Event): void {
    if (event) {
      event.preventDefault();
    }
    const no = this.trackingNumber().trim().toUpperCase();
    if (no) {
      this.executeTrack(no);
    }
  }

  private executeTrack(no: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);

    this.trackingApi
      .track(no)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.result.set(data);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 404) {
              this.error.set(
                `Tracking number "${no}" was not found. Please verify the tracking number and try again.`,
              );
              return;
            }
            if (err.error?.message) {
              this.error.set(err.error.message);
              return;
            }
          }
          this.error.set(
            'Unable to communicate with the Parcel Tracking Service. Please verify the backend service is running.',
          );
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
      return date.toLocaleString();
    } catch {
      return instantString;
    }
  }
}
