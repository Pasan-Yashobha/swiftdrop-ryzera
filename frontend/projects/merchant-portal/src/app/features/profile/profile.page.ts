import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppError, toAppError } from '../../core/http/app-error';
import { MerchantStore } from '../../core/merchant/merchant.store';
import { MerchantProfileRequest } from '../../core/models/merchant.model';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Skeleton } from '../../shared/ui/spinner/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { MerchantForm } from '../merchant-form/merchant-form';

/** Read-only view of the business profile with an edit mode. */
@Component({
  selector: 'app-profile-page',
  imports: [DatePipe, RouterLink, ErrorAlert, Skeleton, StatusBadge, MerchantForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.page.html',
})
export class ProfilePage {
  protected readonly store = inject(MerchantStore);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<AppError | null>(null);

  private readonly toasts = inject(ToastService);

  protected startEditing(): void {
    this.saveError.set(null);
    this.editing.set(true);
  }

  protected stopEditing(): void {
    this.editing.set(false);
  }

  protected reload(): void {
    this.store.refresh().subscribe({ error: () => undefined });
  }

  protected save(request: MerchantProfileRequest): void {
    this.saving.set(true);
    this.saveError.set(null);
    this.store.update(request).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(false);
        this.toasts.success('Profile updated.');
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.saveError.set(toAppError(error));
      },
    });
  }
}
