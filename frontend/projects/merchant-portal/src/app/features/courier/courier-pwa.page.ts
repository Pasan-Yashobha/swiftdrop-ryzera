import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  DeliveryStatus,
  FAILURE_REASONS,
  NEXT_STATUSES,
  RouteStop,
  statusLabel,
} from './courier.models';
import { CourierService } from './courier.service';

@Component({
  selector: 'app-courier-pwa-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  providers: [CourierService],
  templateUrl: './courier-pwa.page.html',
  styleUrl: './courier-pwa.page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourierPwaPage implements OnInit, OnDestroy {
  protected readonly courierService = inject(CourierService);
  private readonly destroyRef = inject(DestroyRef);
  readonly available = this.courierService.available;
  readonly availabilityLoading = this.courierService.availabilityLoading;
  readonly availabilitySaving = this.courierService.availabilitySaving;
  readonly availabilityBusy = this.courierService.availabilityBusy;
  readonly availabilityError = this.courierService.availabilityError;
  readonly availabilityNotice = this.courierService.availabilityNotice;
  reloadAvailability(): void {
    this.courierService.loadAvailability();
  }
  setAvailability(available: boolean): void {
    this.courierService.setAvailability(available);
  }

  readonly locationBusy = signal(false);
  readonly locationNotice = signal('Location permission has not been requested. No continuous tracking.');
  readonly locationError = signal<string | null>(null);
  private locationDestroyed = false;
  private nextLocationReportAt = 0;

  reportLocation(): void {
    if (this.locationBusy()) return;
    this.locationError.set(null);
    this.locationNotice.set('');
    if (Date.now() < this.nextLocationReportAt) {
      this.locationError.set('Wait at least 10 seconds before another report.');
      return;
    }
    if (!navigator.geolocation) {
      this.locationError.set('GPS is unavailable in this browser.');
      return;
    }
    this.locationBusy.set(true);
    this.locationNotice.set('Waiting for location permission and GPS…');
    try {
      navigator.geolocation.getCurrentPosition(
      (position) => {
        if (this.locationDestroyed) return;
        const { latitude: lat, longitude: lng } = position.coords;
        if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180
            || !Number.isFinite(position.timestamp) || position.timestamp < 0 || position.timestamp > Date.now()) {
          this.locationBusy.set(false);
          this.locationNotice.set('');
          this.locationError.set('GPS returned an invalid position or timestamp. Retry outdoors.');
          return;
        }
        this.locationNotice.set('Sending location report…');
        this.courierService.reportLocation(lat, lng, new Date(position.timestamp).toISOString())
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.nextLocationReportAt = Date.now() + 10000;
              this.locationBusy.set(false);
              this.locationNotice.set('Location report confirmed by the messaging service. Tracking updates may take a moment.');
            },
            error: (error) => {
              this.locationBusy.set(false);
              this.locationNotice.set('');
              const status = error?.status;
              this.locationError.set(status === 401 || status === 403 ? 'Sign in with an authorized Courier account.'
                : status === 429 ? 'Wait at least 10 seconds before another report.'
                : status === 400 ? 'Location report was rejected. Request a fresh GPS position.'
                : status === 404 ? 'Courier profile was not found. Complete your profile before reporting.'
                : 'Location report was not confirmed. Check your connection and retry; it may already have reached the broker.');
            },
          });
      },
      (error) => {
        if (this.locationDestroyed) return;
        this.locationBusy.set(false);
        this.locationNotice.set('');
        this.locationError.set(error.code === 1 ? 'Location permission denied. Enable it in browser settings to retry.'
          : error.code === 3 ? 'GPS request timed out. Retry outdoors.' : 'GPS position is unavailable. Try again.');
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 0 },
    );
    } catch {
      this.locationBusy.set(false);
      this.locationNotice.set('');
      this.locationError.set('GPS could not be requested. Check browser permissions and HTTPS.');
    }
  }

  readonly failureReasons = FAILURE_REASONS;
  readonly statusLabel = statusLabel;
  readonly proofPhoto = signal<File | null>(null);
  readonly proofPhotoUrl = signal<string | null>(null);
  readonly photoError = signal<string | null>(null);
  readonly receivedBy = signal('');
  readonly deliveryCode = signal('');
  readonly showFailedSection = signal(false);
  readonly failureReason = signal('');
  readonly formError = signal<string | null>(null);
  readonly activeTab = this.courierService.activeTab;
  readonly selectedStop = this.courierService.selectedStop;
  readonly stops = this.courierService.stops;
  readonly totalStops = this.courierService.totalCount;
  readonly completedStops = this.courierService.completedCount;
  readonly pendingStops = this.courierService.pendingCount;
  readonly loading = this.courierService.loading;
  readonly error = this.courierService.error;
  readonly actionNotice = this.courierService.notice;
  readonly actionError = this.courierService.actionError;
  readonly busy = this.courierService.busy;
  readonly blocked = computed(() => this.busy() || this.courierService.needsRefresh());
  readonly allowedStatuses = computed(() => {
    const status = this.selectedStop()?.status;
    return status ? (NEXT_STATUSES[status] ?? []) : [];
  });
  readonly progressActions = computed(() =>
    this.allowedStatuses().filter((s) => s !== 'DELIVERED' && s !== 'FAILED'),
  );

  ngOnInit(): void {
    this.reload();
    this.courierService.loadAvailability();
  }
  ngOnDestroy(): void {
    this.removePhoto();
  }
  reload(): void {
    if (this.busy()) return;
    // A refreshed list may select a different assignment; never carry its proof draft over.
    this.clearForm();
    this.actionNotice.set(null);
    this.courierService.loadToday();
  }
  selectStop(stop: RouteStop): void {
    if (this.busy()) return;
    this.courierService.selectStop(stop.stopNumber);
    this.clearForm();
  }
  setTab(tab: 'today' | 'history'): void {
    this.courierService.setActiveTab(tab);
  }
  toggleFailedSection(): void {
    this.showFailedSection.update((v) => !v);
  }
  dismissNotice(): void {
    this.actionNotice.set(null);
  }

  onPhotoSelected(event: Event): void {
    if (this.blocked()) return;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.removePhoto();
    if (file.size === 0) this.photoError.set('The image is empty.');
    else if (!['image/jpeg', 'image/jpg', 'image/png', 'image/webp'].includes(file.type)) {
      this.photoError.set('Select a JPG, PNG or WebP image.');
    } else if (file.size > 5 * 1024 * 1024) this.photoError.set('Image must be at most 5 MB.');
    else {
      this.proofPhoto.set(file);
      this.proofPhotoUrl.set(URL.createObjectURL(file));
    }
    input.value = '';
  }
  removePhoto(): void {
    const url = this.proofPhotoUrl();
    if (url) URL.revokeObjectURL(url);
    this.proofPhoto.set(null);
    this.proofPhotoUrl.set(null);
    this.photoError.set(null);
  }
  markStatusAction(status: DeliveryStatus): void {
    if (!this.progressActions().some((action) => action === status)) return;
    const parcelId = this.selectedStop()?.parcelId;
    if (parcelId)
      this.courierService
        .updateStatus(parcelId, { status })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.clearForm());
  }
  markDeliveredAction(): void {
    if (this.blocked() || !this.allowedStatuses().includes('DELIVERED')) return;
    this.formError.set(null);
    const name = this.receivedBy();
    if (!name.trim() || name.length > 100 || name.includes('\u0000')) {
      this.formError.set('Enter the recipient name (1–100 characters, without NUL characters).');
      return;
    }
    if (this.deliveryCode().length > 10) {
      this.formError.set('Optional OTP must be at most 10 characters.');
      return;
    }
    const photo = this.proofPhoto();
    if (!photo) {
      this.photoError.set('Select a proof photo before submitting.');
      return;
    }
    const parcelId = this.selectedStop()?.parcelId;
    if (parcelId)
      this.courierService
        .updateStatus(
          parcelId,
          {
            status: 'DELIVERED',
            recipientName: name,
            ...(this.deliveryCode() ? { otp: this.deliveryCode() } : {}),
          },
          photo,
        )
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.clearForm());
  }
  markFailedAction(): void {
    if (this.blocked() || !this.allowedStatuses().includes('FAILED')) return;
    this.formError.set(null);
    if (!FAILURE_REASONS.some((r) => r.code === this.failureReason())) {
      this.formError.set('Select a supported failure reason.');
      return;
    }
    const parcelId = this.selectedStop()?.parcelId;
    if (parcelId)
      this.courierService
        .updateStatus(parcelId, {
          status: 'FAILED',
          failureReason: this.failureReason(),
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.clearForm());
  }
  private clearForm(): void {
    this.removePhoto();
    this.receivedBy.set('');
    this.deliveryCode.set('');
    this.failureReason.set('');
    this.formError.set(null);
    this.showFailedSection.set(false);
  }
}
