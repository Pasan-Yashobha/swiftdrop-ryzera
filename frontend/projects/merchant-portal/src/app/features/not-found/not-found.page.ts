import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <h1 class="visually-hidden">Page not found</h1>
      <app-empty-state
        heading="We could not find that page"
        message="The link may be old, or the address mistyped."
      >
        <a class="btn btn-primary" routerLink="/">Go to your dashboard</a>
      </app-empty-state>
    </div>
  `,
})
export class NotFoundPage {}
