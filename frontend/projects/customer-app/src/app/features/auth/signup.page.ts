import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-signup-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page" style="max-width: 32rem; margin: 0 auto; padding-block: var(--space-6);">
      <header class="stack" style="text-align: center; margin-bottom: var(--space-4);">
        <h1>Create Customer Account</h1>
        <p class="muted">Sign up to book fast pickups, track parcels, and manage deliveries.</p>
      </header>

      <!-- Error Alert -->
      @if (errorMessage(); as err) {
        <div class="card" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg); margin-bottom: var(--space-3);">
          <p><strong>Registration Error:</strong> {{ err }}</p>
        </div>
      }

      <!-- Signup Card -->
      <section class="card stack">
        <form class="stack" (submit)="onSubmit($event)">
          <div class="form-group">
            <label class="form-label" for="signup-name">Full Name</label>
            <input
              id="signup-name"
              type="text"
              class="form-input"
              placeholder="e.g. Kasun Jayawardena"
              [value]="fullName()"
              (input)="fullName.set($any($event.target).value)"
              required
              autocomplete="name"
              [disabled]="loading()"
            />
            @if (fullName() && fullName().trim().length < 2) {
              <span class="small" style="color: var(--tone-danger-fg);">Full name must be at least 2 characters.</span>
            }
          </div>

          <div class="form-group">
            <label class="form-label" for="signup-email">Email Address</label>
            <input
              id="signup-email"
              type="email"
              class="form-input"
              placeholder="e.g. kasun@example.com"
              [value]="email()"
              (input)="email.set($any($event.target).value.trim())"
              required
              autocomplete="email"
              [disabled]="loading()"
            />
            @if (email() && !isEmailValid()) {
              <span class="small" style="color: var(--tone-danger-fg);">Please enter a valid email address.</span>
            }
          </div>

          <div class="form-group">
            <label class="form-label" for="signup-phone">Phone Number (Optional)</label>
            <input
              id="signup-phone"
              type="tel"
              class="form-input"
              placeholder="+94 77 123 4567"
              [value]="phone()"
              (input)="phone.set($any($event.target).value.trim())"
              autocomplete="tel"
              [disabled]="loading()"
            />
            <span class="muted small">Used for courier delivery notifications & OTP verification.</span>
          </div>

          <div class="grid-2">
            <div class="form-group">
              <label class="form-label" for="signup-password">Password</label>
              <input
                id="signup-password"
                type="password"
                class="form-input"
                placeholder="At least 8 characters"
                [value]="password()"
                (input)="password.set($any($event.target).value)"
                required
                autocomplete="new-password"
                [disabled]="loading()"
              />
              @if (password() && password().length < 8) {
                <span class="small" style="color: var(--tone-danger-fg);">Must be at least 8 characters.</span>
              }
            </div>

            <div class="form-group">
              <label class="form-label" for="signup-confirm">Confirm Password</label>
              <input
                id="signup-confirm"
                type="password"
                class="form-input"
                placeholder="Repeat password"
                [value]="confirmPassword()"
                (input)="confirmPassword.set($any($event.target).value)"
                required
                autocomplete="new-password"
                [disabled]="loading()"
              />
              @if (confirmPassword() && !passwordsMatch()) {
                <span class="small" style="color: var(--tone-danger-fg);">Passwords do not match.</span>
              }
            </div>
          </div>

          <button
            type="submit"
            class="btn btn-primary btn-lg"
            style="width: 100%; margin-top: var(--space-3);"
            [disabled]="loading() || !canSubmit()"
          >
            @if (loading()) {
              Creating Account...
            } @else {
              Create Customer Account
            }
          </button>
        </form>

        <hr style="border: 0; border-top: 1px solid var(--color-border); margin-block: var(--space-3);" />

        <div style="text-align: center; font-size: var(--text-sm);">
          <span class="muted">Already have a customer account? </span>
          <a routerLink="/login" style="color: var(--color-primary); font-weight: 600;">
            Sign in
          </a>
        </div>
      </section>
    </div>
  `,
})
export class SignupPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly fullName = signal('');
  protected readonly email = signal('');
  protected readonly phone = signal('');
  protected readonly password = signal('');
  protected readonly confirmPassword = signal('');
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly isEmailValid = computed(() => {
    const e = this.email();
    return e.length > 3 && e.includes('@') && e.includes('.');
  });

  protected readonly passwordsMatch = computed(() => {
    return this.password() === this.confirmPassword();
  });

  protected readonly canSubmit = computed(() => {
    return (
      this.fullName().trim().length >= 2 &&
      this.isEmailValid() &&
      this.password().length >= 8 &&
      this.passwordsMatch()
    );
  });

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (!this.canSubmit() || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.auth
      .signup({
        fullName: this.fullName().trim(),
        email: this.email().trim().toLowerCase(),
        phone: this.phone().trim() || null,
        password: this.password(),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loading.set(false);
          void this.router.navigateByUrl('/dashboard');
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 409) {
              this.errorMessage.set('An account with this email already exists. Please sign in instead.');
              return;
            }
            if (err.error?.message) {
              this.errorMessage.set(err.error.message);
              return;
            }
          }
          this.errorMessage.set('Registration failed. Please check the backend service connection.');
        },
      });
  }
}
