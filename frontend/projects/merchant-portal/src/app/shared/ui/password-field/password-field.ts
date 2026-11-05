import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { FieldControl, FormField } from '../form-field/form-field';

/** Password input with modern embedded SVG eye show/hide toggle. */
@Component({
  selector: 'app-password-field',
  imports: [ReactiveFormsModule, FormField, FieldControl],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-form-field [label]="label()" [control]="control()" [hint]="hint()" [messages]="messages()">
      <div class="password-wrapper">
        <input
          appFieldControl
          class="password-input"
          [type]="visible() ? 'text' : 'password'"
          [formControl]="control()"
          [autocomplete]="autocomplete()"
        />
        <button
          type="button"
          class="password-toggle-btn"
          [attr.aria-pressed]="visible()"
          [attr.aria-label]="visible() ? 'Hide password' : 'Show password'"
          (click)="visible.set(!visible())"
        >
          @if (visible()) {
            <svg class="eye-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
              <line x1="1" y1="1" x2="23" y2="23"></line>
            </svg>
          } @else {
            <svg class="eye-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
              <circle cx="12" cy="12" r="3"></circle>
            </svg>
          }
          <span class="visually-hidden">{{ visible() ? 'Hide' : 'Show' }} password</span>
        </button>
      </div>
    </app-form-field>
  `,
  styles: `
    .password-wrapper {
      position: relative;
      display: flex;
      align-items: center;
      width: 100%;
    }
    .password-wrapper input {
      width: 100%;
      padding-right: 2.75rem;
    }
    .password-toggle-btn {
      position: absolute;
      right: 0.375rem;
      top: 50%;
      transform: translateY(-50%);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.25rem;
      height: 2.25rem;
      padding: 0;
      border: none;
      background: transparent;
      color: var(--color-text-muted);
      border-radius: var(--radius-sm);
      cursor: pointer;
      transition: color 0.15s ease, background-color 0.15s ease;
    }
    .password-toggle-btn:hover {
      color: var(--color-primary);
      background-color: var(--color-surface-alt);
    }
    .password-toggle-btn:focus-visible {
      outline: 2px solid var(--color-primary);
      outline-offset: 1px;
    }
    .eye-icon {
      pointer-events: none;
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
