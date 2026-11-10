import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewEncapsulation,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { notBlank } from '../../shared/forms/validators';
import { focusFirstInvalid } from '../../shared/forms/focus-first-invalid';
import { AppError, toAppError } from '../../core/http/app-error';
import { AuthUser } from '../../core/models/auth.model';
import { safeReturnUrl } from '../../core/auth/return-url';
import { writeStoredSession } from '../../core/auth/stored-session';
import { setMockMerchantStatus } from '../../core/http/mock-api.interceptor';

const INVALID_CREDENTIALS = new AppError(
  401,
  'INVALID_CREDENTIALS',
  'The email or password is incorrect.',
);

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  templateUrl: './login.page.html',
  styleUrl: './auth.css',
})
export class LoginPage {
  /** Query parameters, bound by the router (withComponentInputBinding). */
  readonly returnUrl = input<string>();
  readonly reason = input<string>();
  readonly error = input<string>();

  readonly oauthErrorMessage = computed(() => {
    const err = this.error();
    if (err === 'oauth_denied') return 'Sign in with Google was cancelled or denied.';
    if (err === 'csrf_detected') return 'Security verification failed. Please try signing in again.';
    if (err === 'unverified_google_email') return 'Your Google email address has not been verified.';
    if (err === 'oauth_failed') return 'Failed to sign in with Google. Please try again.';
    return null;
  });

  protected readonly auth = inject(AuthService);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    rememberMe: new FormControl(true, { nonNullable: true }),
  });

  protected readonly showPassword = signal(false);
  protected readonly busy = signal(false);
  protected readonly formError = signal<AppError | null>(null);
  protected readonly quickTrackStatus = signal<string | null>(null);

  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

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
    const { email, password } = this.form.getRawValue();
    this.signIn(this.auth.login({ email: email.trim(), password }));
  }

  protected signOut(): void {
    this.auth.logout();
  }

  protected enterDemo(status: 'APPROVED' | 'PENDING' | 'NEW' = 'APPROVED'): void {
    setMockMerchantStatus(status);
    const mockUser: AuthUser = {
      id: 'usr-demo-merchant',
      email: 'dhanushkamg1234@gmail.com',
      fullName: 'Dhanushka Madhushanka',
      role: 'MERCHANT',
    };
    writeStoredSession({
      accessToken: 'demo-jwt-token-swiftdrop-merchant',
      refreshToken: null,
      user: mockUser,
    });
    window.location.href = status === 'NEW' ? '/onboarding' : status === 'PENDING' ? '/status' : '/dashboard';
  }

  private signIn(request: Observable<AuthUser>): void {
    this.busy.set(true);
    this.formError.set(null);
    request.subscribe({
      next: (user) => this.onSignedIn(user),
      error: (error: unknown) => {
        this.busy.set(false);
        const appError = toAppError(error);
        this.formError.set(appError.status === 401 ? INVALID_CREDENTIALS : appError);
      },
    });
  }

  private onSignedIn(user: AuthUser): void {
    this.busy.set(false);
    if (user.role === 'MERCHANT') {
      const target = safeReturnUrl(this.returnUrl()) ?? '/dashboard';
      void this.router.navigateByUrl(target);
    } else {
      void this.router.navigateByUrl('/welcome');
    }
  }
}


