import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

interface Presentation {
  label: string;
  tone: BadgeTone;
}

const PRESENTATIONS: Readonly<Record<string, Presentation>> = {
  // Orders
  PENDING: { label: 'Pending', tone: 'warning' },
  CREATED: { label: 'Created', tone: 'info' },
  ASSIGNED: { label: 'Courier assigned', tone: 'info' },
  PICKED_UP: { label: 'Picked up', tone: 'info' },
  IN_TRANSIT: { label: 'In transit', tone: 'info' },
  OUT_FOR_DELIVERY: { label: 'Out for delivery', tone: 'info' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
  FAILED: { label: 'Failed', tone: 'danger' },
  RESCHEDULED: { label: 'Rescheduled', tone: 'warning' },
  RETURNED: { label: 'Returned', tone: 'warning' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral' },
  // Merchants
  APPROVED: { label: 'Approved', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
  SUSPENDED: { label: 'Suspended', tone: 'danger' },
  // API keys and OAuth clients
  ACTIVE: { label: 'Active', tone: 'success' },
  REVOKED: { label: 'Revoked', tone: 'neutral' },
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
 * Pill for an order, merchant, API key or OAuth client status. Colour is never the only signal:
 * every tone has its own symbol and the status is always spelled out.
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
  /** An OrderStatus, MerchantStatus or CredentialStatus value; unknown values render neutrally. */
  readonly status = input.required<string>();

  protected readonly presentation = computed(() => statusPresentation(this.status()));
  protected readonly icon = computed(() => ICONS[this.presentation().tone]);
}
