import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Placeholder for lists with nothing to show. Project a call-to-action button inside the tag. */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-state">
      <h2 class="empty-state__title">{{ heading() }}</h2>
      @if (message()) {
        <p class="muted">{{ message() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-6) var(--space-4);
      border: 1px dashed var(--color-border-strong);
      border-radius: var(--radius-md);
      text-align: center;
    }
  `,
})
export class EmptyState {
  readonly heading = input.required<string>();
  readonly message = input<string>();
}
