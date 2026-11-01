import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FieldControl, FormField } from '../../../shared/ui/form-field/form-field';

export const DEFAULT_GRACE_HOURS = 24;
export const MAX_GRACE_HOURS = 72;

let nextDialogId = 0;

/**
 * Asks how long the old key keeps working after a rotation. Keep one in the template and
 * `await dialog.ask('Shop backend')`: it resolves to the grace period in hours, or null when cancelled.
 */
@Component({
  selector: 'app-rotate-key-dialog',
  imports: [ReactiveFormsModule, FormField, FieldControl],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog" [attr.aria-labelledby]="titleId" (close)="onClosed()">
      @if (keyLabel(); as label) {
        <form class="stack" novalidate [formGroup]="form" (ngSubmit)="confirm()">
          <h2 [id]="titleId">Rotate "{{ label }}"?</h2>
          <p class="dialog__message">
            A new key is created with the same label and environment. The old key keeps working for
            the grace period so you can deploy the new one without downtime, then it stops working.
          </p>
          <app-form-field
            label="Grace period (hours)"
            hint="0 revokes the old key immediately. The maximum is 72 hours."
            [control]="form.controls.graceHours"
            [messages]="{
              required: 'Enter a number of hours between 0 and 72.',
              min: 'The grace period cannot be negative.',
              max: 'The grace period can be at most 72 hours.',
              pattern: 'Enter a whole number of hours.',
            }"
          >
            <input
              #hours
              appFieldControl
              type="number"
              inputmode="numeric"
              formControlName="graceHours"
            />
          </app-form-field>
          <div class="dialog__actions">
            <button type="button" class="btn btn-ghost" (click)="close()">Cancel</button>
            <button type="submit" class="btn btn-primary">Rotate key</button>
          </div>
        </form>
      }
    </dialog>
  `,
  styleUrl: '../../../shared/ui/dialog.css',
})
export class RotateKeyDialog {
  protected readonly titleId = `rotate-key-dialog-title-${nextDialogId++}`;
  protected readonly keyLabel = signal<string | null>(null);
  protected readonly form = new FormGroup({
    graceHours: new FormControl<number | null>(DEFAULT_GRACE_HOURS, {
      validators: [
        Validators.required,
        Validators.min(0),
        Validators.max(MAX_GRACE_HOURS),
        Validators.pattern(/^\d+$/),
      ],
    }),
  });

  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly hoursInput = viewChild<ElementRef<HTMLInputElement>>('hours');
  private resolver: ((hours: number | null) => void) | null = null;

  /** Opens the dialog for the key with this label. Asking again cancels the open question. */
  ask(label: string): Promise<number | null> {
    this.settle(null);
    this.form.reset({ graceHours: DEFAULT_GRACE_HOURS });
    this.keyLabel.set(label);
    // Render the form first so the input exists and can take focus.
    this.changeDetector.detectChanges();
    return new Promise<number | null>((resolve) => {
      this.resolver = resolve;
      const dialog = this.dialog().nativeElement;
      if (!dialog.open) {
        dialog.showModal();
      }
      this.hoursInput()?.nativeElement.focus();
    });
  }

  protected confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.settle(this.form.controls.graceHours.value);
    this.dialog().nativeElement.close();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  /** Fires for every way the dialog can close, including Escape. */
  protected onClosed(): void {
    if (this.dialog().nativeElement.open) {
      return; // A stale close event: the dialog was already reopened for the next question.
    }
    this.settle(null);
    this.keyLabel.set(null);
  }

  private settle(hours: number | null): void {
    this.resolver?.(hours);
    this.resolver = null;
  }
}
