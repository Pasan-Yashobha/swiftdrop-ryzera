import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PricingApi } from '../../core/api/pricing.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { PARCEL_SIZES } from '../../core/models/order.model';
import { DeliveryType, Quote, SizeClass, Zone } from '../../core/models/pricing.model';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../shared/ui/form-field/form-field';
import { Spinner } from '../../shared/ui/spinner/spinner';

/** Weight limits enforced by the Pricing service: above 0 and at most 20 kg, three decimals. */
export const MAX_WEIGHT_KG = 20;

/**
 * Delivery fee estimate for customers and merchants (Pricing service, Strategy pattern on the server).
 * The quote is itemised and stored by the server; any change to the form clears it so an old price is
 * never shown next to new inputs.
 */
@Component({
  selector: 'app-estimate-page',
  imports: [ReactiveFormsModule, DatePipe, MoneyPipe, ErrorAlert, FieldControl, FormField, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './estimate.page.html',
  styleUrl: './pricing.css',
})
export class EstimatePage {
  private readonly api = inject(PricingApi);

  protected readonly sizes = PARCEL_SIZES;
  protected readonly maxWeight = MAX_WEIGHT_KG;
  protected readonly zones = signal<Zone[]>([]);
  protected readonly zonesLoading = signal(true);
  protected readonly zonesError = signal<AppError | null>(null);
  protected readonly quote = signal<Quote | null>(null);
  protected readonly submitting = signal(false);
  protected readonly error = signal<AppError | null>(null);

  protected readonly form = new FormGroup({
    originZone: new FormControl('', { nonNullable: true, validators: Validators.required }),
    destinationZone: new FormControl('', { nonNullable: true, validators: Validators.required }),
    weightKg: new FormControl<number | null>(1, {
      validators: [Validators.required, Validators.min(0.001), Validators.max(MAX_WEIGHT_KG)],
    }),
    sizeClass: new FormControl<SizeClass>('SMALL', { nonNullable: true }),
    deliveryType: new FormControl<DeliveryType>('STANDARD', { nonNullable: true }),
    promoCode: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(30), Validators.pattern(/^\s*[A-Za-z0-9_-]*\s*$/)],
    }),
  });

  /** Increases on every change and request, so only the answer to the latest request is shown. */
  private attempt = 0;

  constructor() {
    this.loadZones();
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.attempt++;
      this.quote.set(null);
      this.error.set(null);
    });
  }

  protected loadZones(): void {
    this.zonesLoading.set(true);
    this.zonesError.set(null);
    this.api.activeZones().subscribe({
      next: (page) => {
        this.zones.set(page.items);
        this.zonesLoading.set(false);
      },
      error: (error: unknown) => {
        this.zonesError.set(toAppError(error));
        this.zonesLoading.set(false);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const current = ++this.attempt;
    this.submitting.set(true);
    this.error.set(null);
    this.api
      .estimate({
        originZone: value.originZone,
        destinationZone: value.destinationZone,
        weightKg: Number(value.weightKg),
        sizeClass: value.sizeClass,
        deliveryType: value.deliveryType,
        promoCode: value.promoCode,
      })
      .subscribe({
        next: (quote) => {
          if (current === this.attempt) {
            this.quote.set(quote);
          }
          this.submitting.set(false);
        },
        error: (error: unknown) => {
          if (current === this.attempt) {
            this.error.set(toAppError(error));
          }
          this.submitting.set(false);
        },
      });
  }

  protected sizeLabel(size: SizeClass): string {
    return size.charAt(0) + size.slice(1).toLowerCase();
  }
}
