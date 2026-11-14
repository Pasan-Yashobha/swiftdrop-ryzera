import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { toAppError } from '../../core/http/app-error';
import { focusFirstInvalid } from '../../shared/forms/focus-first-invalid';
import {
  PASSWORD_RULES,
  passwordPolicy,
  sameAsSibling,
} from '../../shared/forms/validators';
import { openTrackingPage } from '../../core/tracking/quick-track';

@Component({
  selector: 'app-reset-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  templateUrl: './reset-password.page.html',
  styleUrl: './auth.css',
})
export class ResetPasswordPage {
  /** Bound from the URL query parameter ?token=... via withComponentInputBinding */
  readonly token = input<string>();

  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly form = new FormGroup({
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, passwordPolicy],
    }),
    confirmPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, sameAsSibling('password')],
    }),
  });

  protected readonly showPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);
  protected readonly busy = signal(false);
  protected readonly success = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly quickTrackStatus = signal<string | null>(null);

  private readonly passwordValue = toSignal(this.form.controls.password.valueChanges, {
    initialValue: '',
  });

  protected readonly rules = computed(() =>
    PASSWORD_RULES.map((rule) => ({ label: rule.label, met: rule.test(this.passwordValue()) })),
  );

  constructor() {
    this.form.controls.password.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.confirmPassword.updateValueAndValidity());
  }

  protected quickTrack(code: string): void {
    // Opens the public tracking page (Member 8); only an impossible number leaves a hint here.
    this.quickTrackStatus.set(openTrackingPage(this.router, code));
  }

  protected submit(): void {
    const tokenVal = (this.token() || '').trim();
    if (!tokenVal) {
      this.errorMessage.set('Password reset token is missing from the link. Please request a new recovery link.');
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }

    this.busy.set(true);
    this.errorMessage.set(null);

    const newPassword = this.form.controls.password.value;
    this.authApi.resetPassword(tokenVal, newPassword).subscribe({
      next: () => {
        this.busy.set(false);
        this.success.set(true);
        setTimeout(() => {
          void this.router.navigateByUrl('/login');
        }, 3000);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        const appErr = toAppError(err);
        this.errorMessage.set(
          appErr.message ||
            'The password reset link is invalid, expired, or has already been used. Please request a new link.',
        );
      },
    });
  }
}

