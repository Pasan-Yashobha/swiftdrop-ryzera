import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PricingApi } from '../../../core/api/pricing.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { Zone } from '../../../core/models/pricing.model';
import { notBlank } from '../../../shared/forms/validators';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../../shared/ui/form-field/form-field';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { PricingAdminNav } from './pricing-admin-nav';

/** Uppercase letter first, then uppercase letters, digits, _ or - (the Pricing service's rule). */
export const ZONE_CODE_PATTERN = /^[A-Z][A-Z0-9_-]*$/;

/** Admin: delivery zones. Deactivating keeps the zone for history but stops new rate cards and estimates. */
@Component({
  selector: 'app-zones-page',
  imports: [
    ReactiveFormsModule,
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
  templateUrl: './zones.page.html',
  styleUrls: ['../../route-board.css', './admin.css'],
})
export class ZonesPage {
  private readonly api = inject(PricingApi);
  private readonly toast = inject(ToastService);
  private readonly confirm = viewChild.required(ConfirmDialog);

  protected readonly zones = signal<Zone[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<AppError | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<AppError | null>(null);
  protected readonly editing = signal<Zone | null>(null);

  protected readonly form = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(30),
        Validators.pattern(ZONE_CODE_PATTERN),
      ],
    }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlank, Validators.maxLength(100)],
    }),
    description: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) }),
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.all<Zone>('zones').subscribe({
      next: (zones) => {
        this.zones.set(zones);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }

  protected edit(zone: Zone): void {
    this.editing.set(zone);
    this.saveError.set(null);
    this.form.reset({ code: zone.code, name: zone.name, description: zone.description ?? '' });
  }

  protected cancelEdit(): void {
    this.editing.set(null);
    this.saveError.set(null);
    this.form.reset();
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
      .saveZone(
        { code: value.code, name: value.name.trim(), description: value.description.trim() },
        this.editing()?.id,
      )
      .subscribe({
        next: (zone) => {
          this.saving.set(false);
          this.toast.success(`Zone ${zone.code} saved.`);
          this.cancelEdit();
          this.load();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveError.set(toAppError(error));
        },
      });
  }

  protected async deactivate(zone: Zone): Promise<void> {
    const confirmed = await this.confirm().ask({
      title: `Deactivate ${zone.code}?`,
      message:
        'The zone stays in the records, but it can no longer be chosen for new rate cards or estimates.',
      confirmLabel: 'Deactivate',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.api.deactivate('zones', zone.id).subscribe({
      next: () => {
        this.toast.success(`Zone ${zone.code} deactivated.`);
        if (this.editing()?.id === zone.id) {
          this.cancelEdit();
        }
        this.load();
      },
      error: (error: unknown) => this.toast.error(toAppError(error).message),
    });
  }
}
