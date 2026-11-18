import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { sanitizeReturnUrl } from '../../core/guards/auth.guard';

@Component({
  selector: 'app-login-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page" style="max-width: 28rem; margin: 0 auto; padding-block: var(--space-6);">
      <header class="stack" style="text-align: center; margin-bottom: var(--space-4);">
        <h1>Sign In</h1>
        <p class="muted">Access your SwiftDrop customer deliveries, real-time tracking, and receipts.</p>
      </header>

      <!-- Session Expired Notice -->
      @if (sessionExpired()) {
        <div class="card" role="alert" style="border-left: 4px solid var(--tone-warning-fg); background: var(--tone-warning-bg); color: var(--tone-warning-fg); margin-bottom: var(--space-3);">
          <p><strong>Session Expired:</strong> Your session has timed out. Please sign in again to continue.</p>
        </div>
      }

      <!-- Error Alert -->
      @if (errorMessage(); as err) {
        <div class="card" role="alert" style="border-left: 4px solid var(--tone-danger-fg); background: var(--tone-danger-bg); color: var(--tone-danger-fg); margin-bottom: var(--space-3);">
          <p><strong>Sign In Failed:</strong> {{ err }}</p>
        </div>
      }

      <!-- Login Card -->
      <section class="card stack">
        <form class="stack" (submit)="onSubmit($event)">
          <div class="form-group">
            <label class="form-label" for="login-email">Email Address</label>
            <input
              id="login-email"
              type="email"
              class="form-input"
              placeholder="e.g. kasun@example.com"
              [value]="email()"
              (input)="email.set($any($event.target).value.trim())"
              required
              autocomplete="email"
              [disabled]="loading()"
            />
          </div>

          <div class="form-group">
            <label class="form-label" for="login-password">Password</label>
            <input
              id="login-password"
              type="password"
              class="form-input"
              placeholder="••••••••"
              [value]="password()"
              (input)="password.set($any($event.target).value)"
              required
              autocomplete="current-password"
              [disabled]="loading()"
            />
          </div>

          <button
            type="submit"
            class="btn btn-primary btn-lg"
            style="width: 100%; margin-top: var(--space-2);"
            [disabled]="loading() || !canSubmit()"
          >
            @if (loading()) {
              Signing In...
            } @else {
              Sign In
            }
          </button>
        </form>

        <hr style="border: 0; border-top: 1px solid var(--color-border); margin-block: var(--space-3);" />

        <div style="text-align: center; font-size: var(--text-sm);">
          <span class="muted">Don't have an account? </span>
          <a routerLink="/signup" style="color: var(--color-primary); font-weight: 600;">
            Sign up for SwiftDrop
          </a>
        </div>
      </section>
    </div>
  `,
})
export class LoginPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly sessionExpired = signal(false);

  private returnUrl = '/dashboard';

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.returnUrl = sanitizeReturnUrl(params.get('returnUrl'));
      if (params.get('reason') === 'expired') {
        this.sessionExpired.set(true);
      }
    });
  }

  protected canSubmit(): boolean {
    return this.email().includes('@') && this.password().length >= 8;
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (!this.canSubmit() || this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.sessionExpired.set(false);

    this.auth
      .login({
        email: this.email(),
        password: this.password(),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loading.set(false);
          void this.router.navigateByUrl(this.returnUrl);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          if (err instanceof HttpErrorResponse) {
            if (err.status === 401) {
              this.errorMessage.set('The email or password you entered is incorrect.');
              return;
            }
            if (err.status === 429) {
              this.errorMessage.set(
                err.error?.message || 'Account temporarily locked due to multiple failed login attempts.',
              );
              return;
            }
            if (err.error?.message) {
              this.errorMessage.set(err.error.message);
              return;
            }
          }
          this.errorMessage.set('Could not connect to Identity Service. Please check if the backend is running.');
        },
      });
  }
}
