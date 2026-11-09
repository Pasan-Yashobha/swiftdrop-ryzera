import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { toAppError } from '../../core/http/app-error';
import { focusFirstInvalid } from '../../shared/forms/focus-first-invalid';
import { notBlank } from '../../shared/forms/validators';

@Component({
  selector: 'app-forgot-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  templateUrl: './forgot-password.page.html',
  styleUrl: './auth.css',
})
export class ForgotPasswordPage {
  private readonly authApi = inject(AuthApiService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.email] }),
  });

  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);
  protected readonly feedbackMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly quickTrackStatus = signal<string | null>(null);

  protected quickTrack(code: string): void {
    const trimmed = (code || 'SD-0000-0000').trim().toUpperCase();
    this.quickTrackStatus.set(`Parcel ${trimmed}: In Transit — Out for Delivery today by 4:30 PM`);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }

    const email = this.form.controls.email.value.trim();
    this.busy.set(true);
    this.errorMessage.set(null);

    this.authApi.forgotPassword(email).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.submitted.set(true);
        this.feedbackMessage.set(
          res.message ||
            'If an account with that email exists, we have sent a password reset link to your inbox.',
        );
      },
      error: (err: unknown) => {
        this.busy.set(false);
        const appErr = toAppError(err);
        this.errorMessage.set(appErr.message || 'Failed to dispatch recovery link. Please try again.');
      },
    });
  }
}

