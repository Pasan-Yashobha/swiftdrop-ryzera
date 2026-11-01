import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { AppError, toAppError } from '../../core/http/app-error';
import { MerchantStore } from '../../core/merchant/merchant.store';
import { MerchantProfileRequest } from '../../core/models/merchant.model';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Skeleton } from '../../shared/ui/spinner/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { MerchantForm } from '../merchant-form/merchant-form';

/** Where a merchant waits for approval, fixes a rejected application, or learns it has been suspended. */
@Component({
  selector: 'app-status-page',
  imports: [DatePipe, RelativeTimePipe, ErrorAlert, Skeleton, StatusBadge, MerchantForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './status.page.html',
})
export class StatusPage {
  protected readonly store = inject(MerchantStore);
  protected readonly refreshing = signal(false);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<AppError | null>(null);

  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  /** Asks the server again and moves on if the merchant's situation changed. */
  protected refresh(): void {
    this.refreshing.set(true);
    this.store.refresh().subscribe({
      next: (merchant) => {
        this.refreshing.set(false);
        if (merchant === null) {
          void this.router.navigateByUrl('/onboarding');
        } else if (merchant.status === 'APPROVED') {
          this.toasts.success('Your business has been approved. Welcome aboard!');
          void this.router.navigateByUrl('/dashboard');
        } else {
          this.toasts.info('Your status has not changed yet.');
        }
      },
      error: () => this.refreshing.set(false),
    });
  }

  /** Saves the corrected details, then sends the application back for review. */
  protected resubmit(request: MerchantProfileRequest): void {
    this.saving.set(true);
    this.saveError.set(null);
    this.store
      .update(request)
      .pipe(switchMap(() => this.store.resubmit()))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.toasts.success('Application resubmitted for review.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveError.set(toAppError(error));
        },
      });
  }
}
