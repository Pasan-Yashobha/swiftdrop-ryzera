import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/** Shown to signed-in users without the ADMIN role (and reachable directly). */
@Component({
  selector: 'app-not-authorised-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="container page page--narrow">
      <div class="card stack" role="alert">
        <h1>Not authorised</h1>
        @if (auth.user(); as user) {
          <p>
            You are signed in as <strong>{{ user.fullName }}</strong> with the
            <strong>{{ user.role }}</strong> role. The admin console is only available to
            administrators.
          </p>
          <div class="cluster">
            <button type="button" class="btn btn-primary" (click)="auth.logout()">
              Sign in with another account
            </button>
          </div>
        } @else {
          <p>You need to sign in with an administrator account to use the admin console.</p>
          <div class="cluster">
            <a class="btn btn-primary" routerLink="/login">Go to sign in</a>
          </div>
        }
      </div>
    </main>
  `,
})
export class NotAuthorisedPage {
  protected readonly auth = inject(AuthService);
}
