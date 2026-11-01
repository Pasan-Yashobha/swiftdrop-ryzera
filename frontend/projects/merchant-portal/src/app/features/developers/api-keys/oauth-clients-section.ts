import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CredentialApi } from '../../../core/api/credential.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import {
  CredentialEnvironment,
  OAUTH_SCOPES,
  OAuthClient,
  OAuthScope,
} from '../../../core/models/credential.model';
import { focusFirstInvalid } from '../../../shared/forms/focus-first-invalid';
import { noControlCharacters, notBlank } from '../../../shared/forms/validators';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { CopyButton } from '../../../shared/ui/copy-button/copy-button';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { FieldControl, FormField } from '../../../shared/ui/form-field/form-field';
import { SecretOnceDialog } from '../../../shared/ui/secret-once-dialog/secret-once-dialog';
import { Skeleton } from '../../../shared/ui/spinner/skeleton';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { DeveloperError } from '../shared/developer-error';
import { countActive, MAX_ACTIVE_CREDENTIALS } from './credential-format';
import { EnvironmentBadge } from './environment-badge';

const ENVIRONMENT_EXPLANATIONS: Readonly<Record<CredentialEnvironment, string>> = {
  TEST: 'Sandbox: tokens from this client create simulated orders only.',
  LIVE: 'Production: tokens from this client create real parcels.',
};

const SCOPE_DESCRIPTIONS: Readonly<Record<OAuthScope, string>> = {
  'orders:write': 'Create orders and advance sandbox orders',
  'orders:read': 'Read and list orders',
};

/** Lists, creates, rotates and revokes OAuth2 client-credentials clients. The client secret is shown once. */
@Component({
  selector: 'app-oauth-clients-section',
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
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './oauth-clients-section.html',
  styleUrl: '../shared/responsive-table.css',
  styles: `
    .scopes {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      margin: 0;
      padding: 0;
      border: 0;
    }
    .scopes legend {
      padding: 0;
      font-weight: 600;
    }
    .scopes__error {
      color: var(--tone-danger-fg);
      font-weight: 600;
    }
  `,
})
export class OAuthClientsSection {
  protected readonly maxActive = MAX_ACTIVE_CREDENTIALS;
  protected readonly scopeOptions = OAUTH_SCOPES.map((scope) => ({
    value: scope,
    description: SCOPE_DESCRIPTIONS[scope],
  }));
  protected readonly clients = signal<OAuthClient[] | null>(null);
  protected readonly loadError = signal<AppError | null>(null);
  protected readonly actionError = signal<AppError | null>(null);
  protected readonly createError = signal<AppError | null>(null);
  protected readonly creating = signal(false);
  /** The client a rotate or revoke request is running for. */
  protected readonly busyClientId = signal<string | null>(null);

  protected readonly form = new FormGroup({
    label: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(100), noControlCharacters],
    }),
    environment: new FormControl<CredentialEnvironment>('TEST', { nonNullable: true }),
    scopes: new FormControl<OAuthScope[]>([...OAUTH_SCOPES], {
      nonNullable: true,
      validators: Validators.required,
    }),
  });

  private readonly selectedEnvironment = toSignal(this.form.controls.environment.valueChanges, {
    initialValue: this.form.controls.environment.value,
  });
  protected readonly environmentExplanation = computed(
    () => ENVIRONMENT_EXPLANATIONS[this.selectedEnvironment()],
  );
  private readonly selectedScopes = toSignal(this.form.controls.scopes.valueChanges, {
    initialValue: this.form.controls.scopes.value,
  });
  protected readonly activeCount = computed(() => countActive(this.clients() ?? []));
  protected readonly limitReached = computed(() => this.activeCount() >= this.maxActive);

  private readonly api = inject(CredentialApi);
  private readonly toasts = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly secretDialog = viewChild.required(SecretOnceDialog);
  private readonly confirmDialog = viewChild.required(ConfirmDialog);

  constructor() {
    this.load();
  }

  protected isScopeSelected(scope: OAuthScope): boolean {
    return this.selectedScopes().includes(scope);
  }

  protected toggleScope(scope: OAuthScope, selected: boolean): void {
    const control = this.form.controls.scopes;
    const current = control.value;
    control.setValue(
      OAUTH_SCOPES.filter((candidate) =>
        candidate === scope ? selected : current.includes(candidate),
      ),
    );
    control.markAsTouched();
  }

  protected load(): void {
    this.loadError.set(null);
    this.api.listOAuthClients().subscribe({
      next: (clients) => this.clients.set(clients),
      error: (error: unknown) => this.loadError.set(toAppError(error)),
    });
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }
    const { label, environment, scopes } = this.form.getRawValue();
    this.creating.set(true);
    this.createError.set(null);
    this.api.createOAuthClient({ label: label.trim(), environment, scopes }).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.form.reset({ label: '', environment, scopes: [...OAUTH_SCOPES] });
        this.load();
        void this.secretDialog().reveal({
          title: 'OAuth client created',
          label: 'client secret',
          secret: created.clientSecret,
          description: `Your client ID is ${created.clientId}. Use it together with this secret to request access tokens.`,
        });
      },
      error: (error: unknown) => {
        this.creating.set(false);
        this.createError.set(toAppError(error));
      },
    });
  }

  protected async rotateSecret(client: OAuthClient): Promise<void> {
    const confirmed = await this.confirmDialog().ask({
      title: `Rotate the secret of "${client.label}"?`,
      message:
        'The current secret stops working immediately, so update your server with the new secret straight away. Access tokens that were already issued stay valid until they expire.',
      confirmLabel: 'Rotate secret',
    });
    if (!confirmed) {
      return;
    }
    this.busyClientId.set(client.id);
    this.actionError.set(null);
    this.api.rotateOAuthClientSecret(client.id).subscribe({
      next: (rotated) => {
        this.busyClientId.set(null);
        void this.secretDialog().reveal({
          title: 'Client secret rotated',
          label: 'client secret',
          secret: rotated.clientSecret,
          description: 'The previous secret no longer works.',
        });
      },
      error: (error: unknown) => {
        this.busyClientId.set(null);
        this.actionError.set(toAppError(error));
      },
    });
  }

  protected async revoke(client: OAuthClient): Promise<void> {
    const confirmed = await this.confirmDialog().ask({
      title: `Revoke "${client.label}"?`,
      message:
        'The client can no longer request tokens and its existing tokens stop working. This cannot be undone.',
      confirmLabel: 'Revoke client',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.busyClientId.set(client.id);
    this.actionError.set(null);
    this.api.revokeOAuthClient(client.id).subscribe({
      next: () => {
        this.busyClientId.set(null);
        this.toasts.success(`Client "${client.label}" revoked.`);
        this.load();
      },
      error: (error: unknown) => {
        this.busyClientId.set(null);
        this.actionError.set(toAppError(error));
      },
    });
  }
}
