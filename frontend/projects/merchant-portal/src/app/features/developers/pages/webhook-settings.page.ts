import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { WebhookApi } from '../../../core/api/webhook.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import {
  SaveWebhookRequest,
  WebhookConfig,
  WebhookTestResult,
} from '../../../core/models/webhook.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { SecretOnceDialog } from '../../../shared/ui/secret-once-dialog/secret-once-dialog';
import { Skeleton } from '../../../shared/ui/spinner/skeleton';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { DeveloperError } from '../shared/developer-error';
import { WebhookConfigForm } from '../webhooks/webhook-config-form';
import { WebhookDeliveryLog } from '../webhooks/webhook-delivery-log';

/** Where and how SwiftDrop delivers parcel events: endpoint, events, signing secret, test and delivery log. */
@Component({
  selector: 'app-webhook-settings-page',
  imports: [
    DatePipe,
    RouterLink,
    RelativeTimePipe,
    ConfirmDialog,
    SecretOnceDialog,
    Skeleton,
    DeveloperError,
    WebhookConfigForm,
    WebhookDeliveryLog,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './webhook-settings.page.html',
})
export class WebhookSettingsPage {
  protected readonly config = signal<WebhookConfig | null>(null);
  protected readonly loadError = signal<AppError | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<AppError | null>(null);
  protected readonly actionError = signal<AppError | null>(null);
  /** The long-running action in flight: `rotate`, `test` or `delete`. */
  protected readonly busyAction = signal<'rotate' | 'test' | 'delete' | null>(null);
  protected readonly testResult = signal<WebhookTestResult | null>(null);

  /** The settings when a webhook exists; the form and the actions only deal with that case. */
  protected readonly configured = computed(() => {
    const config = this.config();
    return config?.configured ? config : null;
  });

  private readonly api = inject(WebhookApi);
  private readonly toasts = inject(ToastService);
  private readonly secretDialog = viewChild.required(SecretOnceDialog);
  private readonly confirmDialog = viewChild.required(ConfirmDialog);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loadError.set(null);
    this.api.get().subscribe({
      next: (config) => this.config.set(config),
      error: (error: unknown) => this.loadError.set(toAppError(error)),
    });
  }

  protected save(request: SaveWebhookRequest): void {
    const wasConfigured = this.configured() !== null;
    this.saving.set(true);
    this.saveError.set(null);
    this.api.save(request).subscribe({
      next: ({ secret, ...saved }) => {
        this.saving.set(false);
        this.config.set(saved);
        if (secret) {
          void this.secretDialog().reveal({
            title: 'Webhook signing secret',
            label: 'signing secret',
            secret,
            description:
              'Use it to verify the X-SwiftDrop-Signature header of every event we send to your endpoint.',
          });
        } else if (wasConfigured) {
          this.toasts.success('Webhook settings saved.');
        }
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.saveError.set(toAppError(error));
      },
    });
  }

  protected async rotateSecret(): Promise<void> {
    const confirmed = await this.confirmDialog().ask({
      title: 'Rotate the signing secret?',
      message:
        'The current secret stops working immediately. Events signed with the new secret fail verification on your server until you update it.',
      confirmLabel: 'Rotate secret',
    });
    if (!confirmed) {
      return;
    }
    this.startAction('rotate');
    this.api.rotateSecret().subscribe({
      next: ({ secret }) => {
        this.busyAction.set(null);
        this.load();
        void this.secretDialog().reveal({
          title: 'New webhook signing secret',
          label: 'signing secret',
          secret,
          description: 'The previous secret no longer works.',
        });
      },
      error: (error: unknown) => this.failAction(error),
    });
  }

  protected sendTest(): void {
    this.startAction('test');
    this.testResult.set(null);
    this.api.test().subscribe({
      next: (result) => {
        this.busyAction.set(null);
        this.testResult.set(result);
        this.load();
      },
      error: (error: unknown) => this.failAction(error),
    });
  }

  protected async remove(): Promise<void> {
    const confirmed = await this.confirmDialog().ask({
      title: 'Delete the webhook?',
      message:
        'SwiftDrop stops sending events to your endpoint and the signing secret is discarded. You can set up a new webhook later, which gets a new secret.',
      confirmLabel: 'Delete webhook',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.startAction('delete');
    this.api.remove().subscribe({
      next: () => {
        this.busyAction.set(null);
        this.config.set({ configured: false });
        this.testResult.set(null);
        this.toasts.success('Webhook deleted.');
      },
      error: (error: unknown) => this.failAction(error),
    });
  }

  private startAction(action: 'rotate' | 'test' | 'delete'): void {
    this.busyAction.set(action);
    this.actionError.set(null);
  }

  private failAction(error: unknown): void {
    this.busyAction.set(null);
    this.actionError.set(toAppError(error));
  }
}
