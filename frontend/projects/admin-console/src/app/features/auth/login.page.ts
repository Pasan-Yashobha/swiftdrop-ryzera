import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { safeReturnUrl } from '../../core/auth/return-url';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { AppError, toAppError } from '../../core/http/app-error';
import { AuthUser } from '../../core/models/auth.model';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';

const INVALID_CREDENTIALS = new AppError(
  401,
  'INVALID_CREDENTIALS',
  'The email or password is incorrect.',
);

/** Admin sign-in (Identity), plus the development-only dev token sign-in when enabled. */
@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, ErrorAlert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
  styleUrl: './login.page.css',
})
export class LoginPage {
  /** Query parameters, bound by the router (withComponentInputBinding). */
  readonly returnUrl = input<string>();
  readonly reason = input<string>();

  protected readonly devLoginEnabled = inject(APP_CONFIG).devLogin;

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly devForm = new FormGroup({
    subject: new FormControl('admin-1', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly busy = signal(false);
  protected readonly formError = signal<AppError | null>(null);

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.signIn(this.auth.login({ email: email.trim(), password }), true);
  }

  protected submitDev(): void {
    const subject = this.devForm.getRawValue().subject.trim();
    if (!subject) {
      this.devForm.markAllAsTouched();
      return;
    }
    this.signIn(this.auth.devLogin(subject), false);
  }

  private signIn(request: Observable<AuthUser>, credentials: boolean): void {
    this.busy.set(true);
    this.formError.set(null);
    request.subscribe({
      next: (user) => {
        this.busy.set(false);
        const target = user.role === 'ADMIN' ? safeReturnUrl(this.returnUrl()) : '/not-authorised';
        void this.router.navigateByUrl(target);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const appError = toAppError(error);
        this.formError.set(credentials && appError.status === 401 ? INVALID_CREDENTIALS : appError);
      },
    });
  }
}
