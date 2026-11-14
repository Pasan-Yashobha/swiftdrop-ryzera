import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

/** Renders the active toasts inside a polite live region so screen readers announce them. */
@Component({
  selector: 'app-toast-outlet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-region" role="region" aria-label="Notifications" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class]="'toast toast--' + toast.kind">
          <p>{{ toast.message }}</p>
          <button
            type="button"
            class="toast__close"
            aria-label="Dismiss notification"
            (click)="toasts.dismiss(toast.id)"
          >
            <span aria-hidden="true">✕</span>
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .toast-region {
      position: fixed;
      inset: auto var(--space-4) var(--space-4) var(--space-4);
      z-index: 50;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: var(--space-2);
      pointer-events: none;
    }
    .toast {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      width: min(24rem, 100%);
      padding: var(--space-3) var(--space-4);
      border: 1px solid currentcolor;
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-lg);
      pointer-events: auto;
    }
    .toast p {
      flex: 1;
    }
    .toast--success {
      background: var(--tone-success-bg);
      color: var(--tone-success-fg);
    }
    .toast--error {
      background: var(--tone-danger-bg);
      color: var(--tone-danger-fg);
    }
    .toast--info {
      background: var(--tone-info-bg);
      color: var(--tone-info-fg);
    }
    .toast__close {
      border: 0;
      background: none;
      color: inherit;
      font: inherit;
      cursor: pointer;
    }
  `,
})
export class ToastOutlet {
  protected readonly toasts = inject(ToastService);
}
