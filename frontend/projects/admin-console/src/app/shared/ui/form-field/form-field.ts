import {
  ChangeDetectionStrategy,
  Component,
  computed,
  Directive,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { AbstractControl, ValidationErrors, Validators } from '@angular/forms';
import { notBlank } from '../../forms/validators';

let nextFieldId = 0;

type MessageBuilder = (label: string, value: unknown) => string;

const MESSAGES: Readonly<Record<string, MessageBuilder>> = {
  required: (label) => `${label} is required.`,
  email: () => 'Enter a valid email address.',
  minlength: (label, value) =>
    `${label} must be at least ${(value as { requiredLength: number }).requiredLength} characters.`,
  maxlength: (label, value) =>
    `${label} must be at most ${(value as { requiredLength: number }).requiredLength} characters.`,
  min: (label, value) => `${label} must be at least ${(value as { min: number }).min}.`,
  max: (label, value) => `${label} must be at most ${(value as { max: number }).max}.`,
  pattern: (label) => `${label} is not in a valid format.`,
  phone: () => 'Enter a phone number with 9 to 15 digits, for example +94771234567.',
  url: () => 'Enter a full web address starting with http:// or https://.',
  controlChars: (label) => `${label} contains characters that are not allowed.`,
  mismatch: () => 'The values do not match.',
  passwordPolicy: () => 'Choose a password that meets all the rules below.',
  server: (_label, value) => String(value),
};

/**
 * Label, hint and validation message around one form control. Put the control inside and mark it with
 * `appFieldControl`, which wires up its id, `aria-describedby` and `aria-invalid`:
 *
 *   <app-form-field label="Email" [control]="form.controls.email">
 *     <input type="email" appFieldControl [formControl]="form.controls.email" />
 *   </app-form-field>
 *
 * Errors appear once the control has been touched (blur or a submit that called markAllAsTouched()).
 */
@Component({
  selector: 'app-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field">
      <label [for]="controlId">
        {{ label() }}
        @if (required()) {
          <span aria-hidden="true">*</span><span class="visually-hidden">(required)</span>
        } @else {
          <span class="muted field__optional">(optional)</span>
        }
      </label>
      @if (hint()) {
        <p class="muted small" [id]="hintId">{{ hint() }}</p>
      }
      <ng-content />
      @if (errorMessage(); as message) {
        <p class="field__error small" [id]="errorId">{{ message }}</p>
      }
    </div>
  `,
  styles: `
    .field {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }
    .field__optional {
      font-weight: 400;
      font-size: var(--text-sm);
    }
    .field__error {
      color: var(--tone-danger-fg);
      font-weight: 600;
    }
  `,
})
export class FormField {
  readonly label = input.required<string>();
  readonly control = input.required<AbstractControl>();
  readonly hint = input<string>();
  /** Overrides for validation messages, keyed by error name (for example `{ required: 'Tell us your city.' }`). */
  readonly messages = input<Readonly<Record<string, string>>>({});

  readonly controlId = `field-${nextFieldId++}`;
  protected readonly hintId = `${this.controlId}-hint`;
  protected readonly errorId = `${this.controlId}-error`;

  private readonly stateVersion = signal(0);

  protected readonly required = computed(
    () => this.control().hasValidator(Validators.required) || this.control().hasValidator(notBlank),
  );

  protected readonly errorMessage = computed(() => {
    this.stateVersion();
    const control = this.control();
    return control.touched && control.errors ? this.describe(control.errors) : null;
  });

  /** Space-separated ids of the hint and error texts, for `aria-describedby`. */
  readonly describedBy = computed(() => {
    const ids = [this.hint() ? this.hintId : null, this.errorMessage() ? this.errorId : null];
    return ids.filter(Boolean).join(' ') || null;
  });

  readonly showsError = computed(() => this.errorMessage() !== null);

  constructor() {
    // Control state is not a signal; re-evaluate the message whenever the control reports any change.
    effect((onCleanup) => {
      const subscription = this.control().events.subscribe(() =>
        this.stateVersion.update((version) => version + 1),
      );
      onCleanup(() => subscription.unsubscribe());
    });
  }

  private describe(errors: ValidationErrors): string {
    const [name, value] = Object.entries(errors)[0];
    const override = this.messages()[name];
    if (override) {
      return override;
    }
    return MESSAGES[name]?.(this.label(), value) ?? `${this.label()} is not valid.`;
  }
}

/** Marks the control inside an <app-form-field> so the field can label and describe it for assistive tech. */
@Directive({
  selector: '[appFieldControl]',
  host: {
    '[id]': 'field.controlId',
    '[attr.aria-invalid]': 'field.showsError() ? "true" : null',
    '[attr.aria-describedby]': 'field.describedBy()',
  },
})
export class FieldControl {
  protected readonly field = inject(FormField);
}
