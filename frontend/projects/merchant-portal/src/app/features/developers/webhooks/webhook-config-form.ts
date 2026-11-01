import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { AppError } from '../../../core/http/app-error';
import {
  ALL_WEBHOOK_EVENTS,
  SaveWebhookRequest,
  WEBHOOK_EVENT_TYPES,
  WebhookConfigured,
} from '../../../core/models/webhook.model';
import { focusFirstInvalid } from '../../../shared/forms/focus-first-invalid';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { notBlank } from '../../../shared/forms/validators';
import { FieldControl, FormField } from '../../../shared/ui/form-field/form-field';
import { DeveloperError } from '../shared/developer-error';
import { environment } from '../../../../environments/environment';
import { MAX_WEBHOOK_URL_LENGTH, webhookUrl } from './https-url';

/** Either "all events" or at least one specific event must be chosen. */
function eventsChosen(group: AbstractControl): ValidationErrors | null {
  const { allEvents, events } = group.value as { allEvents: boolean; events: string[] };
  return allEvents || events.length > 0 ? null : { noEvents: true };
}

/**
 * Endpoint URL, active switch and event selection of the webhook. "All events" and a specific list
 * are mutually exclusive, as the backend requires. Emits the request; the page does the saving.
 */
@Component({
  selector: 'app-webhook-config-form',
  imports: [ReactiveFormsModule, FormField, FieldControl, DeveloperError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './webhook-config-form.html',
  styles: `
    .events {
      display: grid;
      gap: var(--space-2);
      margin: var(--space-2) 0 0 var(--space-5);
    }
    @media (min-width: 40rem) {
      .events {
        grid-template-columns: 1fr 1fr;
      }
    }
    fieldset {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      margin: 0;
      padding: 0;
      border: 0;
    }
    legend {
      padding: 0;
      margin-bottom: var(--space-2);
      font-weight: 600;
    }
    .events-error {
      color: var(--tone-danger-fg);
      font-weight: 600;
    }
  `,
})
export class WebhookConfigForm {
  /** Existing settings to edit; null for a webhook that is not set up yet. */
  readonly config = input<WebhookConfigured | null>(null);
  readonly busy = input(false);
  readonly submitError = input<AppError | null>(null);
  readonly submitted = output<SaveWebhookRequest>();

  protected readonly eventTypes = WEBHOOK_EVENT_TYPES;
  protected readonly form = new FormGroup(
    {
      url: new FormControl('', {
        nonNullable: true,
        validators: [
          notBlank,
          webhookUrl(environment.devLogin),
          Validators.maxLength(MAX_WEBHOOK_URL_LENGTH),
        ],
      }),
      active: new FormControl(true, { nonNullable: true }),
      allEvents: new FormControl(true, { nonNullable: true }),
      events: new FormControl<string[]>([], { nonNullable: true }),
    },
    { validators: eventsChosen },
  );

  protected readonly allEvents = toSignal(this.form.controls.allEvents.valueChanges, {
    initialValue: this.form.controls.allEvents.value,
  });
  private readonly selectedEvents = toSignal(this.form.controls.events.valueChanges, {
    initialValue: this.form.controls.events.value,
  });

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    effect(() => {
      const config = this.config();
      const allEvents = !config || config.events.includes(ALL_WEBHOOK_EVENTS);
      this.form.reset({
        url: config?.url ?? '',
        active: config?.active ?? true,
        allEvents,
        events: WEBHOOK_EVENT_TYPES.filter((type) => !allEvents && config?.events.includes(type)),
      });
    });
    effect(() => {
      const error = this.submitError();
      if (error) {
        applyServerErrors(this.form, error);
      }
    });
  }

  protected isEventSelected(type: string): boolean {
    return this.selectedEvents().includes(type);
  }

  protected toggleEvent(type: string, selected: boolean): void {
    const current = this.form.controls.events.value;
    this.form.controls.events.setValue(
      WEBHOOK_EVENT_TYPES.filter((candidate) =>
        candidate === type ? selected : current.includes(candidate),
      ),
    );
    this.form.markAsTouched();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      focusFirstInvalid(this.host.nativeElement);
      return;
    }
    const { url, active, allEvents, events } = this.form.getRawValue();
    this.submitted.emit({
      url: url.trim(),
      active,
      events: allEvents ? [ALL_WEBHOOK_EVENTS] : events,
    });
  }
}
