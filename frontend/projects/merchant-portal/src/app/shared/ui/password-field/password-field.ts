import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { FieldControl, FormField } from '../form-field/form-field';

/** Password input with a show / hide toggle, wrapped in a labelled form field. */
@Component({
  selector: 'app-password-field',
  imports: [ReactiveFormsModule, FormField, FieldControl],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-form-field [label]="label()" [control]="control()" [hint]="hint()" [messages]="messages()">
      <div class="password">
        <input
          appFieldControl
          [type]="visible() ? 'text' : 'password'"
          [formControl]="control()"
          [autocomplete]="autocomplete()"
        />
        <button
          type="button"
          class="btn btn-ghost btn-sm"
          [attr.aria-pressed]="visible()"
          (click)="visible.set(!visible())"
        >
          {{ visible() ? 'Hide' : 'Show' }}<span class="visually-hidden"> password</span>
        </button>
      </div>
    </app-form-field>
  `,
  styles: `
    .password {
      display: flex;
      gap: var(--space-2);
    }
    .password input {
      flex: 1;
    }
  `,
})
export class PasswordField {
  readonly label = input('Password');
  readonly control = input.required<FormControl<string>>();
  readonly hint = input<string>();
  readonly messages = input<Readonly<Record<string, string>>>({});
  readonly autocomplete = input<'current-password' | 'new-password'>('current-password');

  protected readonly visible = signal(false);
}
