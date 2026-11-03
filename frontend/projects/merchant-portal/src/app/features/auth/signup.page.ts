import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AppError, toAppError } from '../../core/http/app-error';
import { focusFirstInvalid } from '../../shared/forms/focus-first-invalid';
import { applyServerErrors } from '../../shared/forms/server-errors';
import {
  notBlank,
  PASSWORD_RULES,
  passwordPolicy,
  phoneNumber,
  sameAsSibling,
} from '../../shared/forms/validators';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../shared/ui/form-field/form-field';
import { PasswordField } from '../../shared/ui/password-field/password-field';
import { normalizePhone } from '../../shared/util/phone';

const EMAIL_TAKEN = new AppError(
  409,
  'EMAIL_TAKEN',
  'An account with this email already exists. Try signing in instead.',
);

@Component({
  selector: 'app-signup-page',
  imports: [ReactiveFormsModule, RouterLink, FormField, FieldControl, PasswordField, ErrorAlert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './signup.page.html',
  styleUrl: './auth.css',
})
export class SignupPage {
  protected readonly form = new FormGroup({
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(100)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.email, Validators.maxLength(254)],
    }),
    phone: new FormControl('', { nonNullable: true, validators: [notBlank, phoneNumber] }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, passwordPolicy],
    }),
    confirmPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, sameAsSibling('password')],
    }),
  });

  protected readonly busy = signal(false);
  protected readonly error = signal<AppError | null>(null);

  private readonly password = toSignal(this.form.controls.password.valueChanges, {
    initialValue: '',
  });
  /** The password rules with whether the current input satisfies each, shown as a live checklist. */
  protected readonly rules = computed(() =>
    PASSWORD_RULES.map((rule) => ({ label: rule.label, met: rule.test(this.password()) })),
  );
  protected readonly confirmMessages = { mismatch: 'The passwords do not match.' };

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    this.form.controls.password.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.confirmPassword.updateValueAndValidity());
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth
      .signup({
        fullName: value.fullName.trim(),
        email: value.email.trim(),
        phone: normalizePhone(value.phone),
        password: value.password,
        role: 'MERCHANT',
      })
      .subscribe({
        next: () => void this.router.navigateByUrl('/onboarding'),
        error: (error: unknown) => this.onFailure(toAppError(error)),
      });
  }

  private onFailure(error: AppError): void {
    this.busy.set(false);
    applyServerErrors(this.form, error);
    this.error.set(error.status === 409 ? EMAIL_TAKEN : error);
  }
}
