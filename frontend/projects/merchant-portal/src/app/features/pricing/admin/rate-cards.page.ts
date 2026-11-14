import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { PricingApi } from '../../../core/api/pricing.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { RateCard, Zone } from '../../../core/models/pricing.model';
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

/** Largest fee the Pricing service accepts (NUMERIC with two decimals). */
export const MAX_FEE = 99_999_999.99;

export type FeeName =
  'baseFee' | 'perKgFee' | 'smallSurcharge' | 'mediumSurcharge' | 'largeSurcharge';

/**
 * Admin: rate cards. A card prices one direction between two zones for a period (start inclusive,
 * end exclusive). The server rejects overlapping periods for the same route.
 */
@Component({
  selector: 'app-rate-cards-page',
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
  templateUrl: './rate-cards.page.html',
  styleUrls: ['../../route-board.css', './admin.css'],
})
export class RateCardsPage {
  private readonly api = inject(PricingApi);
  private readonly toast = inject(ToastService);
  private readonly confirm = viewChild.required(ConfirmDialog);

  protected readonly zones = signal<Zone[]>([]);
  protected readonly cards = signal<RateCard[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<AppError | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<AppError | null>(null);
  protected readonly editing = signal<RateCard | null>(null);

  /** New cards need active zones; an inactive zone is still listed when editing a card that uses it. */
  protected readonly zoneOptions = computed(() => {
    const card = this.editing();
    return this.zones().filter(
      (zone) =>
        zone.active || zone.id === card?.originZoneId || zone.id === card?.destinationZoneId,
    );
  });

  protected readonly fees: { name: FeeName; label: string }[] = [
    { name: 'baseFee', label: 'Base fee (LKR)' },
    { name: 'perKgFee', label: 'Per kg (LKR)' },
    { name: 'smallSurcharge', label: 'Small surcharge (LKR)' },
    { name: 'mediumSurcharge', label: 'Medium surcharge (LKR)' },
    { name: 'largeSurcharge', label: 'Large surcharge (LKR)' },
  ];

  protected readonly form = new FormGroup(
    {
      originZoneId: new FormControl('', { nonNullable: true, validators: Validators.required }),
      destinationZoneId: new FormControl('', {
        nonNullable: true,
        validators: Validators.required,
      }),
      baseFee: feeControl(),
      perKgFee: feeControl(),
      smallSurcharge: feeControl(),
      mediumSurcharge: feeControl(),
      largeSurcharge: feeControl(),
      expressMultiplier: new FormControl<number | null>(null, {
        validators: [Validators.min(1), Validators.max(999.99)],
      }),
      validFrom: new FormControl(today(), { nonNullable: true, validators: Validators.required }),
      validTo: new FormControl('', { nonNullable: true }),
    },
    { validators: endAfterStart },
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    forkJoin({
      zones: this.api.all<Zone>('zones'),
      cards: this.api.all<RateCard>('rate-cards'),
    }).subscribe({
      next: ({ zones, cards }) => {
        this.zones.set(zones);
        this.cards.set(cards);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }

  protected zoneName(id: string): string {
    return this.zones().find((zone) => zone.id === id)?.name ?? 'Unknown zone';
  }

  protected edit(card: RateCard): void {
    this.editing.set(card);
    this.saveError.set(null);
    this.form.reset({ ...card, validTo: card.validTo ?? '' });
  }

  protected cancelEdit(): void {
    this.editing.set(null);
    this.saveError.set(null);
    // Fee controls are nullable, so every one must be given a value here or it would come back empty.
    this.form.reset({
      originZoneId: '',
      destinationZoneId: '',
      baseFee: 0,
      perKgFee: 0,
      smallSurcharge: 0,
      mediumSurcharge: 0,
      largeSurcharge: 0,
      expressMultiplier: null,
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
      .saveRateCard(
        {
          originZoneId: value.originZoneId,
          destinationZoneId: value.destinationZoneId,
          baseFee: Number(value.baseFee),
          perKgFee: Number(value.perKgFee),
          smallSurcharge: Number(value.smallSurcharge),
          mediumSurcharge: Number(value.mediumSurcharge),
          largeSurcharge: Number(value.largeSurcharge),
          expressMultiplier:
            value.expressMultiplier === null || String(value.expressMultiplier) === ''
              ? null
              : Number(value.expressMultiplier),
          validFrom: value.validFrom,
          validTo: value.validTo || null,
        },
        this.editing()?.id,
      )
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.toast.success('Rate card saved.');
          this.cancelEdit();
          this.load();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveError.set(toAppError(error));
        },
      });
  }

  protected async deactivate(card: RateCard): Promise<void> {
    const route = `${this.zoneName(card.originZoneId)} → ${this.zoneName(card.destinationZoneId)}`;
    const confirmed = await this.confirm().ask({
      title: `Deactivate the ${route} rate card?`,
      message:
        'Existing quotes keep their prices. The route can then get a new card for this period.',
      confirmLabel: 'Deactivate',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.api.deactivate('rate-cards', card.id).subscribe({
      next: () => {
        this.toast.success(`Rate card ${route} deactivated.`);
        if (this.editing()?.id === card.id) {
          this.cancelEdit();
        }
        this.load();
      },
      error: (error: unknown) => this.toast.error(toAppError(error).message),
    });
  }
}

function feeControl(): FormControl<number | null> {
  return new FormControl<number | null>(0, {
    validators: [Validators.required, Validators.min(0), Validators.max(MAX_FEE)],
  });
}
