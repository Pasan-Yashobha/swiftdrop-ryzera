import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { WebhookAdminApi } from '../../core/api/webhook-admin.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { WebhookDelivery, WebhookDeliveryDetail } from '../../core/models/webhook.model';
import { prettyJson } from '../../shared/util/pretty-json';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { StatusBadge } from '../../shared/ui/status-badge/status-badge';

/**
 * Modal with everything about one webhook delivery: request headers, the pretty-printed payload, the
 * receiver's response snippet and the attempt timeline. All content is rendered as text.
 */
@Component({
  selector: 'app-delivery-detail-dialog',
  imports: [DatePipe, StatusBadge, ErrorAlert, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './delivery-detail-dialog.html',
  styleUrls: ['../../shared/ui/dialog.css', './delivery-detail-dialog.css'],
})
export class DeliveryDetailDialog {
  /** Ids with a replay in flight (owned by the page). */
  readonly replaying = input<ReadonlySet<string>>(new Set());
  /** Asks the page to replay (it confirms first). */
  readonly replay = output<WebhookDelivery>();

  /** The row the dialog was opened for; replaced by fresher data as it arrives. */
  protected readonly summary = signal<WebhookDelivery | null>(null);
  protected readonly detail = signal<WebhookDeliveryDetail | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);

  protected readonly headers = computed(() =>
    Object.entries(this.detail()?.requestHeaders ?? {}).sort(([a], [b]) => a.localeCompare(b)),
  );
  protected readonly payload = computed(() => prettyJson(this.detail()?.payload));

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly api = inject(WebhookAdminApi);
  private request: Subscription | null = null;
  private returnFocusTo: HTMLElement | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  get isOpen(): boolean {
    return this.dialog().nativeElement.open;
  }

  open(delivery: WebhookDelivery, trigger: HTMLElement | null = null): void {
    this.returnFocusTo = trigger;
    this.summary.set(delivery);
    this.detail.set(null);
    const dialog = this.dialog().nativeElement;
    if (!dialog.open) {
      dialog.showModal();
    }
    this.load();
  }

  /** Takes in a row the page received (e.g. after a replay) and reloads the full detail. */
  update(delivery: WebhookDelivery): void {
    if (this.summary()?.id !== delivery.id) {
      return;
    }
    this.summary.set(delivery);
    this.detail.update((detail) => (detail ? { ...detail, ...delivery } : detail));
    if (this.isOpen) {
      this.load();
    }
  }

  protected load(): void {
    const id = this.summary()?.id;
    if (!id) {
      return;
    }
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api.get(id).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.summary.set(detail);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  /** Fires for every way the dialog closes, including Escape. */
  protected onClosed(): void {
    this.request?.unsubscribe();
    this.summary.set(null);
    this.detail.set(null);
    this.returnFocusTo?.focus();
    this.returnFocusTo = null;
  }
}
