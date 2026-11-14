import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="container page page--narrow">
      <div class="card stack">
        <h1>Page not found</h1>
        <p class="muted">The page you asked for does not exist in the admin console.</p>
        <div class="cluster">
          <a class="btn btn-primary" routerLink="/admin">Back to the console</a>
        </div>
      </div>
    </main>
  `,
})
export class NotFoundPage {}
