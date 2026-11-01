import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CredentialApi } from '../../../core/api/credential.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { ApiKey, CredentialEnvironment } from '../../../core/models/credential.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { focusFirstInvalid } from '../../../shared/forms/focus-first-invalid';
import { noControlCharacters, notBlank } from '../../../shared/forms/validators';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { CopyButton } from '../../../shared/ui/copy-button/copy-button';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { FieldControl, FormField } from '../../../shared/ui/form-field/form-field';
import { SecretOnceDialog } from '../../../shared/ui/secret-once-dialog/secret-once-dialog';
import { Skeleton } from '../../../shared/ui/spinner/skeleton';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { focusAfterRender } from '../../../shared/util/focus';
import { DeveloperError } from '../shared/developer-error';
import { countActive, describeExpiry, MAX_ACTIVE_CREDENTIALS } from './credential-format';
import { EnvironmentBadge } from './environment-badge';
import { RotateKeyDialog } from './rotate-key-dialog';

const ENVIRONMENT_EXPLANATIONS: Readonly<Record<CredentialEnvironment, string>> = {
  TEST: 'Sandbox: orders are simulated, no real parcel is created and nobody is charged. Use it while you build your integration.',
  LIVE: 'Production: orders create real parcels that couriers collect and deliver. Keep live keys on your server only.',
};

interface KeyRow {
  key: ApiKey;
  /** `expires in 23h` while the key is in a rotation grace period. */
  expiry: string | null;
  canRotate: boolean;
}

/** Lists, creates, rotates and revokes the merchant's API keys. Secrets only ever live in the one-time dialog. */
@Component({
  selector: 'app-api-keys-section',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RelativeTimePipe,
    ConfirmDialog,
    CopyButton,
    EmptyState,
    FormField,
    FieldControl,
    SecretOnceDialog,
    Skeleton,
    StatusBadge,
    DeveloperError,
    EnvironmentBadge,
    RotateKeyDialog,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './api-keys-section.html',
})
export class ApiKeysSection {
  protected readonly maxActive = MAX_ACTIVE_CREDENTIALS;
  protected readonly keys = signal<ApiKey[] | null>(null);
  protected readonly loadError = signal<AppError | null>(null);
  protected readonly actionError = signal<AppError | null>(null);
  protected readonly createError = signal<AppError | null>(null);
  protected readonly creating = signal(false);
  /** The key a rotate or revoke request is running for. */
  protected readonly busyKeyId = signal<string | null>(null);

  protected readonly form = new FormGroup({
    label: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(100), noControlCharacters],
    }),
    environment: new FormControl<CredentialEnvironment>('TEST', { nonNullable: true }),
  });

  private readonly selectedEnvironment = toSignal(this.form.controls.environment.valueChanges, {
    initialValue: this.form.controls.environment.value,
  });
  protected readonly environmentExplanation = computed(
    () => ENVIRONMENT_EXPLANATIONS[this.selectedEnvironment()],
  );

  protected readonly activeCount = computed(() => countActive(this.keys() ?? []));
  protected readonly limitReached = computed(() => this.activeCount() >= this.maxActive);
  protected readonly rows = computed<KeyRow[]>(() =>
    (this.keys() ?? []).map((key) => ({
      key,
      expiry: describeExpiry(key.expiresAt),
      canRotate: key.status === 'ACTIVE' && key.expiresAt === null,
    })),
  );

  private readonly api = inject(CredentialApi);
  private readonly toasts = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly secretDialog = viewChild.required(SecretOnceDialog);
  private readonly confirmDialog = viewChild.required(ConfirmDialog);
  private readonly rotateDialog = viewChild.required(RotateKeyDialog);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loadError.set(null);
    this.api.listApiKeys().subscribe({
      next: (keys) => this.keys.set(keys),
      error: (error: unknown) => this.loadError.set(toAppError(error)),
    });
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }
    const { label, environment } = this.form.getRawValue();
    this.creating.set(true);
    this.createError.set(null);
    this.api.createApiKey({ label: label.trim(), environment }).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.form.reset({ label: '', environment });
        this.load();
        void this.secretDialog().reveal({
          title: 'API key created',
          label: 'API key',
          secret: created.apiKey,
          description: `Use it in the X-API-Key header of your ${environment === 'LIVE' ? 'live' : 'sandbox'} requests.`,
        });
      },
      error: (error: unknown) => {
        this.creating.set(false);
        this.createError.set(toAppError(error));
      },
    });
  }

  protected async rotate(key: ApiKey): Promise<void> {
    const graceHours = await this.rotateDialog().ask(key.label);
    if (graceHours === null) {
      return;
    }
    this.busyKeyId.set(key.id);
    this.actionError.set(null);
    this.api.rotateApiKey(key.id, { graceHours }).subscribe({
      next: (created) => {
        this.busyKeyId.set(null);
        this.load();
        void this.secretDialog().reveal({
          title: 'API key rotated',
          label: 'API key',
          secret: created.apiKey,
          description:
            graceHours === 0
              ? 'The previous key was revoked immediately.'
              : `The previous key keeps working for ${graceHours} more ${graceHours === 1 ? 'hour' : 'hours'}.`,
        });
      },
      error: (error: unknown) => {
        this.busyKeyId.set(null);
        this.actionError.set(toAppError(error));
      },
    });
  }

  protected async revoke(key: ApiKey): Promise<void> {
    const confirmed = await this.confirmDialog().ask({
      title: `Revoke "${key.label}"?`,
      message:
        'Requests that use this key start failing immediately. This cannot be undone; create a new key if you need one later.',
      confirmLabel: 'Revoke key',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.busyKeyId.set(key.id);
    this.actionError.set(null);
    this.api.revokeApiKey(key.id).subscribe({
      next: () => {
        this.busyKeyId.set(null);
        this.toasts.success(`Key "${key.label}" revoked.`);
        this.load();
        // The row's buttons are gone, so keyboard users continue from the section heading.
        focusAfterRender(this.injector, () => this.heading()?.nativeElement);
      },
      error: (error: unknown) => {
        this.busyKeyId.set(null);
        this.actionError.set(toAppError(error));
      },
    });
  }
}
