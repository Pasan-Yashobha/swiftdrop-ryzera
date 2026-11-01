import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AppError, toAppError } from '../../core/http/app-error';
import { MerchantStore } from '../../core/merchant/merchant.store';
import { MerchantProfileRequest } from '../../core/models/merchant.model';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { MerchantForm } from '../merchant-form/merchant-form';

/** Step two of getting started: register the business so an administrator can approve it. */
@Component({
  selector: 'app-onboarding-page',
  imports: [MerchantForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--medium">
      <div>
        <h1>Register your business</h1>
        <p class="page__lead">
          Tell us about your business. A SwiftDrop administrator reviews every application before
          you can create orders or API keys.
        </p>
      </div>
      <div class="card">
        <app-merchant-form
          submitLabel="Submit for approval"
          [defaultEmail]="auth.user()?.email ?? ''"
          [busy]="busy()"
          [submitError]="error()"
          (submitted)="register($event)"
        />
      </div>
    </div>
  `,
})
export class OnboardingPage {
  protected readonly auth = inject(AuthService);
  protected readonly busy = signal(false);
  protected readonly error = signal<AppError | null>(null);

  private readonly store = inject(MerchantStore);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  protected register(request: MerchantProfileRequest): void {
    this.busy.set(true);
    this.error.set(null);
    this.store.register(request).subscribe({
      next: () => {
        this.toasts.success('Application submitted. We will notify you once it has been reviewed.');
        void this.router.navigateByUrl('/status');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const appError = toAppError(error);
        if (appError.code === 'MERCHANT_ALREADY_REGISTERED') {
          this.store.invalidate();
          void this.router.navigateByUrl('/status');
          return;
        }
        this.error.set(appError);
      },
    });
  }
}
