import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppError } from '../../../core/http/app-error';
import { ErrorAlert } from '../../../shared/ui/error-alert/error-alert';

/**
 * Error banner for the developer pages. An unapproved merchant (403 MERCHANT_NOT_APPROVED) gets an
 * explanation and a link to the application status instead of a generic failure message.
 */
@Component({
  selector: 'app-developer-error',
  imports: [ErrorAlert, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as e) {
      @if (e.code === 'MERCHANT_NOT_APPROVED') {
        <div class="alert alert--warning stack" role="alert">
          <p>{{ e.message }}</p>
          <p>
            Credentials and webhooks become available once your business is approved.
            <a routerLink="/status">Check your application status</a>.
          </p>
        </div>
      } @else {
        <app-error-alert
          [error]="e"
          [retryable]="retryable()"
          [showDetails]="showDetails()"
          (retry)="retry.emit()"
        />
      }
    }
  `,
})
export class DeveloperError {
  readonly error = input<AppError | null>(null);
  readonly retryable = input(false);
  readonly showDetails = input(false);
  readonly retry = output<void>();
}
