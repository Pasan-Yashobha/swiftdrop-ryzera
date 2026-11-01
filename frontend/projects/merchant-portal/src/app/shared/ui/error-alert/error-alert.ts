import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppError } from '../../../core/http/app-error';

/**
 * Error banner for an AppError: the safe message, the support reference when the server sent one, and an
 * optional retry button. Renders nothing while `error` is null.
 */
@Component({
  selector: 'app-error-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as e) {
      <div class="alert alert--danger stack" role="alert">
        <p>{{ e.message }}</p>
        @if (showDetails() && e.details.length > 0) {
          <ul>
            @for (detail of e.details; track $index) {
              <li>{{ detail.field }}: {{ detail.message }}</li>
            }
          </ul>
        }
        @if (e.correlationId) {
          <p class="small">
            Reference: <span class="mono">{{ e.correlationId }}</span>
          </p>
        }
        @if (retryable()) {
          <div>
            <button type="button" class="btn btn-ghost btn-sm" (click)="retry.emit()">
              Try again
            </button>
          </div>
        }
      </div>
    }
  `,
})
export class ErrorAlert {
  readonly error = input<AppError | null>(null);
  /** Lists the per-field validation details under the message. */
  readonly showDetails = input(false);
  /** Shows a "Try again" button that emits `retry`. */
  readonly retryable = input(false);
  readonly retry = output<void>();
}
