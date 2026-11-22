import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  linkedSignal,
  viewChildren,
} from '@angular/core';
import { Router } from '@angular/router';
import { NotificationLogs } from './notification-logs';
import { SmsInbox } from './sms-inbox';
import { TemplatesEditor } from './templates-editor';

export type NotificationTab = 'logs' | 'sms' | 'templates';

interface TabDef {
  id: NotificationTab;
  label: string;
}

const TABS: readonly TabDef[] = [
  { id: 'logs', label: 'Notification log' },
  { id: 'sms', label: 'Mock SMS inbox' },
  { id: 'templates', label: 'Templates' },
];

function toTab(value: string | undefined): NotificationTab {
  return TABS.some((tab) => tab.id === value) ? (value as NotificationTab) : 'logs';
}

/**
 * /admin/notifications: the Notification Service's admin page with three tabs (WAI-ARIA tabs pattern,
 * arrow keys move between tabs). The selected tab is kept in the `?tab=` query parameter.
 */
@Component({
  selector: 'app-notifications-page',
  imports: [NotificationLogs, SmsInbox, TemplatesEditor],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header>
        <h1>Notifications</h1>
        <p class="page__lead">
          E-mail and SMS sent by the Notification Service for parcel events.
        </p>
      </header>

      <div class="tabs" role="tablist" aria-label="Notification views">
        @for (tab of tabs; track tab.id) {
          <button
            #tabButton
            type="button"
            role="tab"
            class="tabs__tab"
            [id]="'tab-' + tab.id"
            [attr.aria-selected]="active() === tab.id"
            [attr.aria-controls]="'panel-' + tab.id"
            [tabIndex]="active() === tab.id ? 0 : -1"
            (click)="select(tab.id)"
            (keydown)="onKeydown($event, $index)"
          >
            {{ tab.label }}
          </button>
        }
      </div>

      <div
        role="tabpanel"
        class="tabs__panel"
        [id]="'panel-' + active()"
        [attr.aria-labelledby]="'tab-' + active()"
      >
        @switch (active()) {
          @case ('sms') {
            <app-sms-inbox />
          }
          @case ('templates') {
            <app-templates-editor />
          }
          @default {
            <app-notification-logs />
          }
        }
      </div>
    </div>
  `,
})
export class NotificationsPage {
  /** `?tab=` query parameter, bound by the router. */
  readonly tab = input<string>();

  protected readonly tabs = TABS;
  protected readonly active = linkedSignal<NotificationTab>(() => toTab(this.tab()));

  private readonly tabButtons = viewChildren<ElementRef<HTMLButtonElement>>('tabButton');
  private readonly router = inject(Router);

  protected select(tab: NotificationTab): void {
    this.active.set(tab);
    void this.router.navigate([], {
      queryParams: { tab: tab === 'logs' ? null : tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected onKeydown(event: KeyboardEvent, index: number): void {
    const target = this.targetIndex(event.key, index);
    if (target === null) {
      return;
    }
    event.preventDefault();
    this.select(this.tabs[target].id);
    this.tabButtons()[target]?.nativeElement.focus();
  }

  private targetIndex(key: string, index: number): number | null {
    const count = this.tabs.length;
    switch (key) {
      case 'ArrowRight':
        return (index + 1) % count;
      case 'ArrowLeft':
        return (index - 1 + count) % count;
      case 'Home':
        return 0;
      case 'End':
        return count - 1;
      default:
        return null;
    }
  }
}
