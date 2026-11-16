import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ParcelApi } from '../../core/api/parcel.api';
import { PricingApi } from '../../core/api/pricing.api';
import {
  CreateCustomerParcelRequest,
  CustomerParcelDetail,
  DeliveryType,
  ParcelSize,
} from '../../core/models/parcel.model';
import {
  PricingEstimateRequest,
  PricingEstimateResponse,
  PricingZone,
} from '../../core/models/pricing.model';
import { generateId } from '../../core/util/id.util';

@Component({
  selector: 'app-book-delivery-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1>Book a Delivery</h1>
          <p class="page__lead">Select delivery zones, calculate instant upfront pricing, and confirm courier pickup.</p>
        </div>
        <a routerLink="/dashboard" class="btn btn-secondary btn-sm">&larr; Back to Dashboard</a>
      </header>

      <!-- Booking Confirmation View -->
      @if (bookedParcel(); as parcel) {
        <section class="card stack" role="status" aria-live="polite" style="border-left: 4px solid var(--tone-success-fg); background: var(--tone-success-bg);">
          <div class="cluster" style="justify-content: space-between;">
            <div>
              <h2 style="color: var(--tone-success-fg);">Booking Confirmed!</h2>
              <p style="margin-top: var(--space-1); color: var(--tone-success-fg);">
                Your parcel delivery has been booked successfully and a courier pickup has been scheduled.
              </p>
            </div>
            <span class="badge badge--created" style="font-size: var(--text-md); padding: var(--space-2) var(--space-4);">
              {{ parcel.status }}
            </span>
          </div>

          <div class="card stack" style="background: var(--color-surface); margin-block: var(--space-3);">
            <div class="grid-3">
              <div>
                <span class="muted small">Tracking Number</span>
                <p class="mono" style="font-size: var(--text-lg); font-weight: 700;">{{ parcel.trackingNumber }}</p>
              </div>
              <div>
                <span class="muted small">Recipient</span>
                <p><strong>{{ parcel.recipient.name }}</strong> ({{ parcel.recipient.phone }})</p>
              </div>
              <div>
                <span class="muted small">Total Fee</span>
                <p style="font-weight: 700; color: var(--color-primary);">
                  {{ parcel.pricing?.currency || 'LKR' }} {{ parcel.pricing?.totalFee?.toFixed(2) }}
                </p>
              </div>
            </div>

            <div class="cluster" style="border-top: 1px solid var(--color-border); padding-top: var(--space-3); margin-top: var(--space-2);">
              <span class="muted small">Route:</span>
              <span><strong>{{ parcel.pickup.city }}</strong> &rarr; <strong>{{ parcel.dropoff.city }}</strong> ({{ parcel.deliveryType }})</span>
            </div>
          </div>

          <div class="cluster" style="gap: var(--space-3);">
            <a [routerLink]="['/parcels', parcel.id]" class="btn btn-primary btn-sm">
              View Parcel Details &rarr;
            </a>
            <a [routerLink]="['/track']" [queryParams]="{ trackingNo: parcel.trackingNumber }" class="btn btn-secondary btn-sm">
              Track Publicly
            </a>
            <a routerLink="/history" class="btn btn-secondary btn-sm">
              Delivery History
            </a>
            <button type="button" class="btn btn-outline btn-sm" (click)="resetForm()">
              + Book Another Delivery
            </button>
          </div>
        </section>
      } @else {
        <div class="grid-2">
          <!-- Booking Form Layout -->
          <section aria-labelledby="form-heading" class="card stack">
            <h2 id="form-heading">Delivery Details</h2>

            <!-- Zone Loading / Error State -->
            @if (loadingZones()) {
              <div class="card" style="padding: var(--space-3); text-align: center;">
                <p class="muted small">Loading available delivery zones from Pricing Service...</p>
              </div>
            } @else if (zonesError(); as zErr) {
              <div class="card" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg);">
                <p><strong>Zone Error:</strong> {{ zErr }}</p>
                <button type="button" class="btn btn-secondary btn-sm" (click)="loadZones()" style="margin-top: var(--space-2);">
                  Retry Loading Zones
                </button>
              </div>
            }

            <form class="stack" (submit)="$event.preventDefault()">
              <!-- Recipient Section -->
              <fieldset class="stack" style="border: none; padding: 0; margin: 0;">
                <legend><h3>1. Recipient Information</h3></legend>
                <div class="form-group">
                  <label class="form-label" for="recipient-name">Recipient Full Name *</label>
                  <input
                    id="recipient-name"
                    type="text"
                    class="form-input"
                    placeholder="e.g. Kasun Jayawardena"
                    [value]="recipientName()"
                    (input)="recipientName.set($any($event.target).value)"
                    required
                  />
                  @if (recipientName() && recipientName().trim().length < 2) {
                    <span class="small" style="color: var(--tone-danger-fg);">Recipient name must be at least 2 characters.</span>
                  }
                </div>
                <div class="grid-2">
                  <div class="form-group">
                    <label class="form-label" for="recipient-phone">Phone Number *</label>
                    <input
                      id="recipient-phone"
                      type="tel"
                      class="form-input"
                      placeholder="+94 77 123 4567"
                      [value]="recipientPhone()"
                      (input)="recipientPhone.set($any($event.target).value)"
                      required
                    />
                    @if (recipientPhone() && !isPhoneValid()) {
                      <span class="small" style="color: var(--tone-danger-fg);">Enter a valid phone number (e.g. +94771234567).</span>
                    }
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="recipient-email">Email (Optional)</label>
                    <input
                      id="recipient-email"
                      type="email"
                      class="form-input"
                      placeholder="kasun@example.com"
                      [value]="recipientEmail()"
                      (input)="recipientEmail.set($any($event.target).value)"
                    />
                  </div>
                </div>
              </fieldset>

              <hr style="border: 0; border-top: 1px solid var(--color-border); margin-block: var(--space-2);" />

              <!-- Pickup & Drop-off Addresses -->
              <fieldset class="stack" style="border: none; padding: 0; margin: 0;">
                <legend><h3>2. Pickup & Drop-off Locations</h3></legend>

                <!-- Pickup Section -->
                <div class="grid-2">
                  <div class="form-group">
                    <label class="form-label" for="origin-zone">Origin / Pickup Zone *</label>
                    <select
                      id="origin-zone"
                      class="form-select"
                      [value]="originZone()"
                      (change)="onOriginZoneChange($any($event.target).value)"
                      [disabled]="loadingZones() || zones().length === 0"
                    >
                      <option value="" disabled>Select Pickup Zone</option>
                      @for (zone of zones(); track zone.code) {
                        <option [value]="zone.code">{{ zone.name }} ({{ zone.code }})</option>
                      }
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="pickup-city">Pickup City *</label>
                    <input
                      id="pickup-city"
                      type="text"
                      class="form-input"
                      placeholder="e.g. Colombo 03"
                      [value]="pickupCity()"
                      (input)="pickupCity.set($any($event.target).value)"
                      required
                    />
                  </div>
                </div>

                <div class="grid-2">
                  <div class="form-group">
                    <label class="form-label" for="pickup-address">Pickup Address Line *</label>
                    <input
                      id="pickup-address"
                      type="text"
                      class="form-input"
                      placeholder="Street Address, Building, Floor"
                      [value]="pickupAddress()"
                      (input)="pickupAddress.set($any($event.target).value)"
                      required
                    />
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="pickup-postal">Postal Code</label>
                    <input
                      id="pickup-postal"
                      type="text"
                      class="form-input"
                      placeholder="00300"
                      [value]="pickupPostalCode()"
                      (input)="pickupPostalCode.set($any($event.target).value)"
                    />
                  </div>
                </div>

                <!-- Drop-off Section -->
                <div class="grid-2" style="margin-top: var(--space-3);">
                  <div class="form-group">
                    <label class="form-label" for="destination-zone">Destination / Drop-off Zone *</label>
                    <select
                      id="destination-zone"
                      class="form-select"
                      [value]="destinationZone()"
                      (change)="onDestinationZoneChange($any($event.target).value)"
                      [disabled]="loadingZones() || zones().length === 0"
                    >
                      <option value="" disabled>Select Destination Zone</option>
                      @for (zone of zones(); track zone.code) {
                        <option [value]="zone.code">{{ zone.name }} ({{ zone.code }})</option>
                      }
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="dropoff-city">Drop-off City *</label>
                    <input
                      id="dropoff-city"
                      type="text"
                      class="form-input"
                      placeholder="e.g. Kandy"
                      [value]="dropoffCity()"
                      (input)="dropoffCity.set($any($event.target).value)"
                      required
                    />
                  </div>
                </div>

                <div class="grid-2">
                  <div class="form-group">
                    <label class="form-label" for="dropoff-address">Drop-off Address Line *</label>
                    <input
                      id="dropoff-address"
                      type="text"
                      class="form-input"
                      placeholder="Destination Address, Street"
                      [value]="dropoffAddress()"
                      (input)="dropoffAddress.set($any($event.target).value)"
                      required
                    />
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="dropoff-postal">Postal Code</label>
                    <input
                      id="dropoff-postal"
                      type="text"
                      class="form-input"
                      placeholder="20000"
                      [value]="dropoffPostalCode()"
                      (input)="dropoffPostalCode.set($any($event.target).value)"
                    />
                  </div>
                </div>
              </fieldset>

              <hr style="border: 0; border-top: 1px solid var(--color-border); margin-block: var(--space-2);" />

              <!-- Parcel Package Specifications -->
              <fieldset class="stack" style="border: none; padding: 0; margin: 0;">
                <legend><h3>3. Package Details & Priority</h3></legend>
                <div class="grid-2">
                  <div class="form-group">
                    <label class="form-label" for="parcel-weight">Weight (kg, 0.01 - 20.00) *</label>
                    <input
                      id="parcel-weight"
                      type="number"
                      step="0.01"
                      min="0.01"
                      max="20"
                      class="form-input"
                      placeholder="1.5"
                      [value]="weightKg()"
                      (input)="onWeightChange($any($event.target).value)"
                      required
                    />
                    @if (!isWeightValid()) {
                      <span class="small" style="color: var(--tone-danger-fg);">Weight must be between 0.01 and 20.00 kg.</span>
                    }
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="parcel-size">Package Size Category *</label>
                    <select
                      id="parcel-size"
                      class="form-select"
                      [value]="size()"
                      (change)="onSizeChange($any($event.target).value)"
                    >
                      <option value="SMALL">Small (Documents, Envelope, Phone)</option>
                      <option value="MEDIUM">Medium (Shoebox, Small Bag)</option>
                      <option value="LARGE">Large (Carton, Appliance)</option>
                    </select>
                  </div>
                </div>

                <div class="grid-2">
                  <div class="form-group">
                    <label class="form-label" for="delivery-type">Delivery Priority *</label>
                    <select
                      id="delivery-type"
                      class="form-select"
                      [value]="deliveryType()"
                      (change)="onDeliveryTypeChange($any($event.target).value)"
                    >
                      <option value="STANDARD">Standard Delivery</option>
                      <option value="EXPRESS">Express Priority</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="promo-code">Promo Code (Optional)</label>
                    <input
                      id="promo-code"
                      type="text"
                      class="form-input"
                      placeholder="e.g. SAVE10"
                      [value]="promoCode()"
                      (input)="onPromoCodeChange($any($event.target).value)"
                      style="text-transform: uppercase;"
                    />
                  </div>
                </div>

                <div class="form-group">
                  <label class="form-label" for="parcel-description">Package Description</label>
                  <input
                    id="parcel-description"
                    type="text"
                    class="form-input"
                    placeholder="e.g. Official documents and passport"
                    [value]="description()"
                    (input)="description.set($any($event.target).value)"
                  />
                </div>

                <div class="form-group">
                  <label class="form-label" for="parcel-notes">Handling Instructions / Notes</label>
                  <textarea
                    id="parcel-notes"
                    rows="2"
                    class="form-textarea"
                    placeholder="e.g. Call sender before pickup, handle with care"
                    [value]="notes()"
                    (input)="notes.set($any($event.target).value)"
                  ></textarea>
                </div>
              </fieldset>
            </form>
          </section>

          <!-- Upfront Pricing & Submission Sidebar -->
          <aside aria-labelledby="quote-heading" class="stack">
            <div class="card stack" style="position: sticky; top: calc(var(--header-height) + var(--space-4));">
              <h2 id="quote-heading">Pricing & Quote Acceptance</h2>
              <p class="muted small">
                SwiftDrop calculates an upfront, guaranteed price quote before booking.
              </p>

              <!-- Estimation Error Alert -->
              @if (estimateError(); as err) {
                <div class="card" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg); padding: var(--space-3);">
                  <p><strong>Pricing Error:</strong> {{ err }}</p>
                </div>
              }

              <!-- Booking Error Alert -->
              @if (bookingError(); as bErr) {
                <div class="card" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg); padding: var(--space-3);">
                  <p><strong>Booking Error:</strong> {{ bErr }}</p>
                </div>
              }

              <!-- Current Quote Display -->
              @if (currentQuote(); as quote) {
                <div class="stack" style="margin-block: var(--space-2); border-top: 1px dashed var(--color-border); border-bottom: 1px dashed var(--color-border); padding-block: var(--space-3);">
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Base Amount:</span>
                    <span>{{ quote.currency }} {{ quote.amounts.baseAmount.toFixed(2) }}</span>
                  </div>
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Weight Charge:</span>
                    <span>{{ quote.currency }} {{ quote.amounts.weightAmount.toFixed(2) }}</span>
                  </div>
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Size Allowance:</span>
                    <span>{{ quote.currency }} {{ quote.amounts.sizeAmount.toFixed(2) }}</span>
                  </div>
                  <div class="cluster" style="justify-content: space-between;">
                    <span class="muted">Express Priority:</span>
                    <span>{{ quote.currency }} {{ quote.amounts.expressAmount.toFixed(2) }}</span>
                  </div>
                  @if (quote.amounts.discountAmount > 0) {
                    <div class="cluster" style="justify-content: space-between; color: var(--tone-success-fg);">
                      <span>Discount ({{ quote.promoCode || 'Promo' }}):</span>
                      <span>-{{ quote.currency }} {{ quote.amounts.discountAmount.toFixed(2) }}</span>
                    </div>
                  }
                </div>

                <div class="cluster" style="justify-content: space-between; font-size: var(--text-lg); font-weight: 700;">
                  <span>Total Rate:</span>
                  <span style="color: var(--color-primary);">{{ quote.currency }} {{ quote.amounts.totalAmount.toFixed(2) }}</span>
                </div>

                <!-- Quote Expiry Info -->
                @if (isQuoteExpired()) {
                  <div class="card" role="alert" style="background: var(--tone-danger-bg); color: var(--tone-danger-fg); padding: var(--space-2); font-size: var(--text-sm);">
                    <strong>Quote Expired:</strong> This quote is past its 15-minute validity window. Please calculate a fresh estimate to continue.
                  </div>
                } @else {
                  <div class="cluster" style="justify-content: space-between; font-size: var(--text-sm); color: var(--color-text-muted);">
                    <span>Quote Valid Until:</span>
                    <span class="mono">{{ formatExpiryTime(quote.expiresAt) }}</span>
                  </div>
                }

                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  (click)="onRequestEstimate()"
                  [disabled]="estimating() || submitting()"
                >
                  Recalculate Estimate
                </button>
              } @else {
                <div class="card" style="padding: var(--space-4); text-align: center; background: var(--color-surface-alt);">
                  <p class="muted small">
                    Select origin/destination zones and package details, then click below to receive an official quote from the Pricing Service.
                  </p>
                </div>

                <button
                  type="button"
                  class="btn btn-secondary btn-lg"
                  style="width: 100%; margin-top: var(--space-2);"
                  [disabled]="!canRequestEstimate() || estimating()"
                  (click)="onRequestEstimate()"
                >
                  @if (estimating()) {
                    Calculating Upfront Price...
                  } @else {
                    Calculate Price Estimate
                  }
                </button>
              }

              <!-- Confirm & Book Button -->
              <button
                type="button"
                class="btn btn-primary btn-lg"
                style="margin-top: var(--space-2); width: 100%;"
                [disabled]="!canConfirmBooking() || submitting()"
                (click)="onConfirmBooking()"
              >
                @if (submitting()) {
                  Confirming Booking...
                } @else {
                  Confirm & Book Delivery
                }
              </button>

              @if (!currentQuote()) {
                <p class="muted small" style="text-align: center;">
                  A verified pricing quote is required before booking can be submitted.
                </p>
              } @else if (!isRecipientValid() || !isPickupValid() || !isDropoffValid()) {
                <p class="muted small" style="text-align: center; color: var(--tone-warning-fg);">
                  Please complete all required recipient, pickup, and drop-off fields.
                </p>
              }
            </div>
          </aside>
        </div>
      }
    </div>
  `,
})
export class BookDeliveryPage implements OnInit {
  private readonly pricingApi = inject(PricingApi);
  private readonly parcelApi = inject(ParcelApi);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * The Idempotency-Key of the latest booking submission. Submitting the same booking again (a retry after
   * a timeout or network error) reuses it, so the backend cannot create duplicate parcels; changing anything starts a new attempt.
   */
  private attempt: { fingerprint: string; idempotencyKey: string } | null = null;

  // Delivery Zones
  protected readonly zones = signal<PricingZone[]>([]);
  protected readonly loadingZones = signal(false);
  protected readonly zonesError = signal<string | null>(null);

  // Recipient Information
  protected readonly recipientName = signal('');
  protected readonly recipientPhone = signal('');
  protected readonly recipientEmail = signal('');

  // Pickup Location
  protected readonly originZone = signal('');
  protected readonly pickupCity = signal('');
  protected readonly pickupAddress = signal('');
  protected readonly pickupPostalCode = signal('');

  // Dropoff Location
  protected readonly destinationZone = signal('');
  protected readonly dropoffCity = signal('');
  protected readonly dropoffAddress = signal('');
  protected readonly dropoffPostalCode = signal('');

  // Package Specifications
  protected readonly weightKg = signal<number>(1.0);
  protected readonly size = signal<ParcelSize>('SMALL');
  protected readonly deliveryType = signal<DeliveryType>('STANDARD');
  protected readonly description = signal('');
  protected readonly notes = signal('');
  protected readonly promoCode = signal('');

  // Pricing & Booking State
  protected readonly currentQuote = signal<PricingEstimateResponse | null>(null);
  protected readonly estimating = signal(false);
  protected readonly estimateError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly bookingError = signal<string | null>(null);
  protected readonly bookedParcel = signal<CustomerParcelDetail | null>(null);

  // Validation Computeds
  protected readonly isPhoneValid = computed(() => {
    const p = this.recipientPhone().trim();
    return /^\+?[0-9]{9,15}$/.test(p);
  });

  protected readonly isRecipientValid = computed(() => {
    return this.recipientName().trim().length >= 2 && this.isPhoneValid();
  });

  protected readonly isPickupValid = computed(() => {
    return (
      this.pickupAddress().trim().length > 0 &&
      this.pickupCity().trim().length > 0 &&
      !!this.originZone()
    );
  });

  protected readonly isDropoffValid = computed(() => {
    return (
      this.dropoffAddress().trim().length > 0 &&
      this.dropoffCity().trim().length > 0 &&
      !!this.destinationZone()
    );
  });

  protected readonly isWeightValid = computed(() => {
    const w = this.weightKg();
    return typeof w === 'number' && !isNaN(w) && w > 0 && w <= 20;
  });

  protected readonly canRequestEstimate = computed(() => {
    return (
      !!this.originZone() &&
      !!this.destinationZone() &&
      this.isWeightValid() &&
      !this.estimating()
    );
  });

  protected readonly isQuoteExpired = computed(() => {
    const quote = this.currentQuote();
    if (!quote) return false;
    return Date.now() >= new Date(quote.expiresAt).getTime();
  });

  protected readonly canConfirmBooking = computed(() => {
    return (
      !!this.currentQuote() &&
      !this.isQuoteExpired() &&
      this.isRecipientValid() &&
      this.isPickupValid() &&
      this.isDropoffValid() &&
      !this.submitting() &&
      !this.estimating()
    );
  });

  ngOnInit(): void {
    this.loadZones();
  }

  protected loadZones(): void {
    this.loadingZones.set(true);
    this.zonesError.set(null);

    this.pricingApi
      .getZones()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (zones) => {
          this.zones.set(zones);
          this.loadingZones.set(false);
          if (zones.length > 0) {
            if (!this.originZone()) {
              this.originZone.set(zones[0].code);
            }
            if (!this.destinationZone()) {
              this.destinationZone.set(zones.length > 1 ? zones[1].code : zones[0].code);
            }
          }
        },
        error: () => {
          this.loadingZones.set(false);
          this.zonesError.set('Unable to load delivery zones from Pricing Service. Please retry.');
        },
      });
  }

  // Field change handlers that invalidate existing quotes
  protected onOriginZoneChange(zoneCode: string): void {
    this.originZone.set(zoneCode);
    this.invalidateQuote();
  }

  protected onDestinationZoneChange(zoneCode: string): void {
    this.destinationZone.set(zoneCode);
    this.invalidateQuote();
  }

  protected onWeightChange(val: string): void {
    const num = parseFloat(val);
    this.weightKg.set(isNaN(num) ? 0 : num);
    this.invalidateQuote();
  }

  protected onSizeChange(sizeVal: ParcelSize): void {
    this.size.set(sizeVal);
    this.invalidateQuote();
  }

  protected onDeliveryTypeChange(typeVal: DeliveryType): void {
    this.deliveryType.set(typeVal);
    this.invalidateQuote();
  }

  protected onPromoCodeChange(promo: string): void {
    this.promoCode.set(promo.trim().toUpperCase());
    this.invalidateQuote();
  }

  private invalidateQuote(): void {
    if (this.currentQuote()) {
      this.currentQuote.set(null);
      this.estimateError.set(null);
    }
  }

  // Calculate upfront price quote
  protected onRequestEstimate(): void {
    if (!this.canRequestEstimate() || this.estimating()) {
      return;
    }

    this.estimating.set(true);
    this.estimateError.set(null);
    this.bookingError.set(null);

    const req: PricingEstimateRequest = {
      originZone: this.originZone(),
      destinationZone: this.destinationZone(),
      weightKg: Math.round(this.weightKg() * 1000) / 1000,
      sizeClass: this.size(),
      deliveryType: this.deliveryType(),
      promoCode: this.promoCode().trim() || null,
    };

    this.pricingApi
      .estimate(req)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (quote) => {
          this.currentQuote.set(quote);
          this.estimating.set(false);
        },
        error: (err: unknown) => {
          this.estimating.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 422) {
              this.estimateError.set(
                err.error?.message || 'Unsupported delivery route, invalid express option, or invalid promo code.',
              );
              return;
            }
            if (err.status === 400) {
              this.estimateError.set(err.error?.message || 'Invalid pricing request parameters.');
              return;
            }
            if (err.status === 401 || err.status === 403) {
              this.estimateError.set('Authentication required. Please sign in to calculate pricing.');
              return;
            }
            if (err.error?.message) {
              this.estimateError.set(err.error.message);
              return;
            }
          }
          this.estimateError.set('Failed to calculate pricing estimate. Please check your network connection.');
        },
      });
  }

  private idempotencyKeyFor(request: CreateCustomerParcelRequest): string {
    const fingerprint = JSON.stringify(request);
    if (this.attempt?.fingerprint !== fingerprint) {
      this.attempt = { fingerprint, idempotencyKey: generateId() };
    }
    return this.attempt.idempotencyKey;
  }

  // Confirm booking with accepted quote
  protected onConfirmBooking(): void {
    const quote = this.currentQuote();
    if (!quote || !this.canConfirmBooking() || this.submitting()) {
      return;
    }

    if (this.isQuoteExpired()) {
      this.bookingError.set('Your price quote has expired. Please calculate a fresh estimate before booking.');
      this.currentQuote.set(null);
      return;
    }

    this.submitting.set(true);
    this.bookingError.set(null);

    const bookingRequest: CreateCustomerParcelRequest = {
      recipient: {
        name: this.recipientName().trim(),
        phone: this.recipientPhone().trim(),
        email: this.recipientEmail().trim() || null,
      },
      pickup: {
        addressLine: this.pickupAddress().trim(),
        city: this.pickupCity().trim(),
        postalCode: this.pickupPostalCode().trim() || null,
      },
      dropoff: {
        addressLine: this.dropoffAddress().trim(),
        city: this.dropoffCity().trim(),
        postalCode: this.dropoffPostalCode().trim() || null,
      },
      weightKg: this.weightKg(),
      size: this.size(),
      deliveryType: this.deliveryType(),
      description: this.description().trim() || null,
      notes: this.notes().trim() || null,
      quoteId: quote.quoteId,
    };

    const idempotencyKey = this.idempotencyKeyFor(bookingRequest);

    this.parcelApi
      .create(bookingRequest, idempotencyKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (parcel) => {
          this.attempt = null;
          this.submitting.set(false);
          this.bookedParcel.set(parcel);
          this.currentQuote.set(null); // prevent duplicate booking
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 409) {
              if (err.error?.code === 'IDEMPOTENCY_KEY_IN_USE') {
                this.bookingError.set('A booking request is currently being processed. Please wait a moment.');
                return;
              }
              this.bookingError.set(
                'The price quote expired or has already been accepted. Please recalculate your estimate.',
              );
              this.currentQuote.set(null);
              return;
            }
            if (err.status === 410) {
              this.bookingError.set(
                'The price quote expired or has already been accepted. Please recalculate your estimate.',
              );
              this.currentQuote.set(null);
              return;
            }
            if (err.status === 422) {
              if (err.error?.code === 'IDEMPOTENCY_KEY_REUSED') {
                this.bookingError.set('A booking attempt conflict occurred. Please recalculate and try again.');
                this.attempt = null;
                return;
              }
              this.bookingError.set(
                err.error?.message || 'Booking validation failed. Please check your delivery information.',
              );
              return;
            }
            if (err.status === 401 || err.status === 403) {
              this.bookingError.set('Authentication required. Please sign in to book your delivery.');
              return;
            }
            if (err.error?.message) {
              this.bookingError.set(err.error.message);
              return;
            }
          }
          this.bookingError.set('Failed to submit delivery booking. Please check your network connection.');
        },
      });
  }

  protected resetForm(): void {
    this.attempt = null;
    this.bookedParcel.set(null);
    this.currentQuote.set(null);
    this.estimateError.set(null);
    this.bookingError.set(null);
    this.recipientName.set('');
    this.recipientPhone.set('');
    this.recipientEmail.set('');
    this.pickupAddress.set('');
    this.pickupCity.set('');
    this.pickupPostalCode.set('');
    this.dropoffAddress.set('');
    this.dropoffCity.set('');
    this.dropoffPostalCode.set('');
    this.weightKg.set(1.0);
    this.size.set('SMALL');
    this.deliveryType.set('STANDARD');
    this.description.set('');
    this.notes.set('');
    this.promoCode.set('');
  }

  protected formatExpiryTime(expiresAtString: string): string {
    try {
      const d = new Date(expiresAtString);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return expiresAtString;
    }
  }
}
