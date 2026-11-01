import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Grey placeholder lines shown while content loads. Announces `label` once to screen readers. */
@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="skeleton" role="status">
      <span class="visually-hidden">{{ label() }}</span>
      @for (line of lineItems(); track line) {
        <span class="skeleton__line" aria-hidden="true"></span>
      }
    </div>
  `,
  styles: `
    .skeleton {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .skeleton__line {
      height: 1rem;
      border-radius: var(--radius-sm);
      background: linear-gradient(
        90deg,
        var(--color-surface-alt),
        var(--color-border),
        var(--color-surface-alt)
      );
      background-size: 200% 100%;
      animation: shimmer 1.4s ease-in-out infinite;
    }
    .skeleton__line:last-child {
      width: 60%;
    }
    @keyframes shimmer {
      from {
        background-position: 200% 0;
      }
      to {
        background-position: -200% 0;
      }
    }
  `,
})
export class Skeleton {
  readonly lines = input(3);
  readonly label = input('Loading');

  protected readonly lineItems = computed(() =>
    Array.from({ length: this.lines() }, (_, index) => index),
  );
}
