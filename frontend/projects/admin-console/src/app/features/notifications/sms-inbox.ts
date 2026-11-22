import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { NotificationAdminApi } from '../../core/api/notification-admin.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { SmsInboxMessage } from '../../core/models/notification.model';
import { PageResponse } from '../../core/models/page.model';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';

export const SMS_PAGE_SIZE = 20;

/** Messages captured by the development mock SMS provider, newest first. */
@Component({
  selector: 'app-sms-inbox',
  imports: [DatePipe, Pagination, ErrorAlert, EmptyState, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="stack" aria-labelledby="sms-inbox-title">
      <div class="page__header">
        <div>
          <h2 id="sms-inbox-title">Mock SMS inbox</h2>
          <p class="muted small">
            SMS sent through the development mock provider. Nothing here reached a real phone.
          </p>
        </div>
        <button type="button" class="btn btn-ghost btn-sm" [disabled]="loading()" (click)="load()">
          {{ loading() ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>

      <app-error-alert [error]="error()" [retryable]="true" (retry)="load()" />

      @if (result(); as page) {
        @if (page.content.length === 0) {
          <app-empty-state
            heading="The inbox is empty"
            message="SMS notifications sent while the mock provider is active show up here."
          />
        } @else {
          <ul class="sms-list" [attr.aria-busy]="loading()">
            @for (sms of page.content; track sms.id) {
              <li class="sms card">
                <p class="sms__meta small">
                  <span class="mono">To {{ sms.to }}</span>
                  <time class="muted" [attr.datetime]="sms.receivedAt">{{
                    sms.receivedAt | date: 'medium'
                  }}</time>
                </p>
                <p class="sms__message">{{ sms.message }}</p>
              </li>
            }
          </ul>
          <app-pagination [info]="page" (pageChange)="goToPage($event)" />
        }
      } @else if (loading()) {
        <app-spinner label="Loading the SMS inbox" />
      }
    </section>
  `,
  styles: `
    .sms-list {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .sms {
      padding: var(--space-3) var(--space-4);
    }
    .sms__meta {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: var(--space-2);
    }
    .sms__message {
      margin-top: var(--space-2);
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
  `,
})
export class SmsInbox {
  protected readonly result = signal<PageResponse<SmsInboxMessage> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);

  private readonly api = inject(NotificationAdminApi);
  private pageIndex = 0;
  private request: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
    this.load();
  }

  protected goToPage(page: number): void {
    this.pageIndex = page;
    this.load();
  }

  protected load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api.smsInbox(this.pageIndex, SMS_PAGE_SIZE).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }
}
