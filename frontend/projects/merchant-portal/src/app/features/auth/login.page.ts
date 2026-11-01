import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/return-url';
import { APP_CONFIG } from '../../core/config/app-config';
import { AppError, toAppError } from '../../core/http/app-error';
import { AuthUser } from '../../core/models/auth.model';
import { focusFirstInvalid } from '../../shared/forms/focus-first-invalid';
import { notBlank } from '../../shared/forms/validators';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../shared/ui/form-field/form-field';
import { PasswordField } from '../../shared/ui/password-field/password-field';

const INVALID_CREDENTIALS = new AppError(
  401,
  'INVALID_CREDENTIALS',
  'The email or password is incorrect.',
);
const NOT_A_MERCHANT = new AppError(
  403,
  'NOT_A_MERCHANT',
  'This portal is for merchant accounts only.',
);

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, FormField, FieldControl, PasswordField, ErrorAlert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
})
export class LoginPage {
  /** Query parameters, bound by the router (withComponentInputBinding). */
  readonly returnUrl = input<string>();
  readonly reason = input<string>();

  protected readonly auth = inject(AuthService);
  protected readonly devLoginEnabled = inject(APP_CONFIG).devLogin;

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly devSubject = new FormControl('user-1', {
    nonNullable: true,
    validators: [notBlank],
  });

  protected readonly busy = signal(false);
  protected readonly error = signal<AppError | null>(null);

  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.signIn(this.auth.login({ email: email.trim(), password }));
  }

  protected devSignIn(): void {
    if (this.devSubject.invalid) {
      this.devSubject.markAsTouched();
      return;
    }
    this.signIn(this.auth.devLogin(this.devSubject.value.trim()));
  }

  protected signOut(): void {
    this.auth.logout();
  }

  private signIn(request: Observable<AuthUser>): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (user) => this.onSignedIn(user),
      error: (error: unknown) => {
        this.busy.set(false);
        const appError = toAppError(error);
        this.error.set(appError.status === 401 ? INVALID_CREDENTIALS : appError);
      },
    });
  }

  private onSignedIn(user: AuthUser): void {
    if (user.role !== 'MERCHANT') {
      this.auth.logout();
      this.busy.set(false);
      this.error.set(NOT_A_MERCHANT);
      return;
    }
    void this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
  }
}
