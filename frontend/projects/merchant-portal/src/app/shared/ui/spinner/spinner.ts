import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Inline loading indicator with a visible, screen-reader-announced label. */
@Component({
  selector: 'app-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="spinner" role="status">
      <span class="spinner__ring" aria-hidden="true"></span>
      <span>{{ label() }}</span>
    </span>
  `,
  styles: `
    .spinner {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      color: var(--color-text-muted);
    }
    .spinner__ring {
      width: 1.25rem;
      height: 1.25rem;
      border: 3px solid var(--color-border);
      border-top-color: var(--color-primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class Spinner {
  readonly label = input('Loading');
}
