import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

interface Presentation {
  label: string;
  tone: BadgeTone;
}

const PRESENTATIONS: Readonly<Record<string, Presentation>> = {
  // Notifications
  PENDING: { label: 'Pending', tone: 'info' },
  SENT: { label: 'Sent', tone: 'success' },
  FAILED: { label: 'Failed', tone: 'danger' },
  SKIPPED: { label: 'Skipped', tone: 'neutral' },
  // Webhook deliveries
  RETRYING: { label: 'Retrying', tone: 'warning' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
  // Webhook attempt outcomes
  RETRY: { label: 'Retry', tone: 'warning' },
  SUCCESS: { label: 'Success', tone: 'success' },
  // Templates
  ACTIVE: { label: 'Active', tone: 'success' },
  INACTIVE: { label: 'Inactive', tone: 'neutral' },
};

const ICONS: Readonly<Record<BadgeTone, string>> = {
  success: '✓',
  danger: '✕',
  warning: '!',
  info: '●',
  neutral: '○',
};

export function statusPresentation(status: string): Presentation {
  return PRESENTATIONS[status] ?? { label: humanize(status), tone: 'neutral' };
}

function humanize(status: string): string {
  const words = status.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * Pill for a notification, delivery or attempt status. Colour is never the only signal: every tone has
 * its own symbol and the status is always spelled out.
 */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="'badge badge--' + presentation().tone">
    <span class="badge__icon" aria-hidden="true">{{ icon() }}</span
    >{{ presentation().label }}
  </span>`,
  styles: `
    .badge {
      display: inline-flex;
      align-items: center;
      gap: var(--space-1);
      padding: 0.125rem var(--space-3);
      border-radius: 999px;
      font-size: var(--text-sm);
      font-weight: 600;
      white-space: nowrap;
    }
    .badge__icon {
      font-size: 0.75em;
    }
    .badge--success {
      background: var(--tone-success-bg);
      color: var(--tone-success-fg);
    }
    .badge--warning {
      background: var(--tone-warning-bg);
      color: var(--tone-warning-fg);
    }
    .badge--danger {
      background: var(--tone-danger-bg);
      color: var(--tone-danger-fg);
    }
    .badge--info {
      background: var(--tone-info-bg);
      color: var(--tone-info-fg);
    }
    .badge--neutral {
      background: var(--tone-neutral-bg);
      color: var(--tone-neutral-fg);
    }
  `,
})
export class StatusBadge {
  /** A status value; unknown values render neutrally with a humanised label. */
  readonly status = input.required<string>();

  protected readonly presentation = computed(() => statusPresentation(this.status()));
  protected readonly icon = computed(() => ICONS[this.presentation().tone]);
}
