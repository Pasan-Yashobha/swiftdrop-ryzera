import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CredentialEnvironment } from '../../../core/models/credential.model';

/** LIVE / TEST pill. The word is always shown, so colour is never the only signal. */
@Component({
  selector: 'app-environment-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="env" [class.env--live]="environment() === 'LIVE'">{{
    environment()
  }}</span>`,
  styles: `
    .env {
      display: inline-block;
      padding: 0.125rem var(--space-3);
      border: 1px solid currentcolor;
      border-radius: var(--radius-sm);
      background: var(--tone-info-bg);
      color: var(--tone-info-fg);
      font-size: var(--text-sm);
      font-weight: 700;
      letter-spacing: 0.04em;
    }
    .env--live {
      background: var(--tone-warning-bg);
      color: var(--tone-warning-fg);
    }
  `,
})
export class EnvironmentBadge {
  readonly environment = input.required<CredentialEnvironment>();
}
