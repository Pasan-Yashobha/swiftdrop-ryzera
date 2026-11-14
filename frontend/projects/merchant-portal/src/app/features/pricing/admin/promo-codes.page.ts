import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PricingApi } from '../../../core/api/pricing.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { DiscountType, PromoCode } from '../../../core/models/pricing.model';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../../shared/ui/form-field/form-field';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { endAfterStart, today } from './date-range';
import { PricingAdminNav } from './pricing-admin-nav';

/** Largest amount the Pricing service accepts (NUMERIC with two decimals). */
const MAX_FEE = 99_999_999.99;
const MAX_PERCENT = 100;
const MAX_USES = 2_147_483_647;

/**
 * Admin: promo codes. Estimates check a code but never use it up; uses are counted at booking.
 * Codes are case-insensitive (the server stores them in capitals).
 */
@Component({
  selector: 'app-promo-codes-page',
  imports: [
    ReactiveFormsModule,
    MoneyPipe,
    ConfirmDialog,
    EmptyState,
    ErrorAlert,
    FieldControl,
    FormField,
    PricingAdminNav,
    Spinner,
    StatusBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './promo-codes.page.html',
  styleUrls: ['../../route-board.css', './admin.css'],
})
export class PromoCodesPage {
  private readonly api = inject(PricingApi);
  private readonly toast = inject(ToastService);
  private readonly confirm = viewChild.required(ConfirmDialog);

  protected readonly promos = signal<PromoCode[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<AppError | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<AppError | null>(null);
  protected readonly editing = signal<PromoCode | null>(null);

  protected readonly form = new FormGroup(
    {
      code: new FormControl('', {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.maxLength(30),
          Validators.pattern(/^[A-Za-z0-9_-]+$/),
        ],
      }),
      discountType: new FormControl<DiscountType>('PERCENT', { nonNullable: true }),
      discountValue: new FormControl<number | null>(10, {
        validators: discountValidators('PERCENT'),
      }),
      minOrderValue: new FormControl<number | null>(0, {
        validators: [Validators.required, Validators.min(0), Validators.max(MAX_FEE)],
      }),
      maxUses: new FormControl<number | null>(null, {
        validators: [Validators.min(1), Validators.max(MAX_USES), Validators.pattern(/^\d*$/)],
      }),
      validFrom: new FormControl(today(), { nonNullable: true, validators: Validators.required }),
      validTo: new FormControl('', { nonNullable: true }),
    },
    { validators: endAfterStart },
  );

  constructor() {
    this.load();
    // A percentage cannot exceed 100; a fixed LKR amount can be larger.
    this.form.controls.discountType.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) => {
      const value = this.form.controls.discountValue;
      value.setValidators(discountValidators(type));
      value.updateValueAndValidity();
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.all<PromoCode>('promo-codes').subscribe({
      next: (promos) => {
        this.promos.set(promos);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }

  protected edit(promo: PromoCode): void {
    this.editing.set(promo);
    this.saveError.set(null);
    this.form.reset({ ...promo, validTo: promo.validTo ?? '' });
  }

  protected cancelEdit(): void {
    this.editing.set(null);
    this.saveError.set(null);
    this.form.reset({
      discountType: 'PERCENT',
      discountValue: 10,
      minOrderValue: 0,
      maxUses: null,
      validFrom: today(),
      validTo: '',
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.saveError.set(null);
    this.api
      .savePromoCode(
        {
          code: value.code.trim(),
          discountType: value.discountType,
          discountValue: Number(value.discountValue),
          minOrderValue: Number(value.minOrderValue),
          maxUses:
            value.maxUses === null || String(value.maxUses) === '' ? null : Number(value.maxUses),
          validFrom: value.validFrom,
          validTo: value.validTo || null,
        },
        this.editing()?.id,
      )
      .subscribe({
        next: (promo) => {
          this.saving.set(false);
          this.toast.success(`Promo code ${promo.code} saved.`);
          this.cancelEdit();
          this.load();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveError.set(toAppError(error));
        },
      });
  }

  protected async deactivate(promo: PromoCode): Promise<void> {
    const confirmed = await this.confirm().ask({
      title: `Deactivate ${promo.code}?`,
      message: 'Customers will no longer be able to use this code.',
      confirmLabel: 'Deactivate',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.api.deactivate('promo-codes', promo.id).subscribe({
      next: () => {
        this.toast.success(`Promo code ${promo.code} deactivated.`);
        if (this.editing()?.id === promo.id) {
          this.cancelEdit();
        }
        this.load();
      },
      error: (error: unknown) => this.toast.error(toAppError(error).message),
    });
  }
}

function discountValidators(type: DiscountType) {
  return [
    Validators.required,
    Validators.min(0.01),
    Validators.max(type === 'PERCENT' ? MAX_PERCENT : MAX_FEE),
  ];
}
