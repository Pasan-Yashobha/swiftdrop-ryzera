import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Styles the confirm button as destructive. */
  danger?: boolean;
}

let nextDialogId = 0;

/**
 * Modal yes/no question built on the native <dialog> element (focus trap, Escape and inert background
 * come from the browser). Keep one in the template and `await dialog.ask({...})`.
 */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog" [attr.aria-labelledby]="titleId" (close)="onClosed()">
      @if (options(); as o) {
        <h2 [id]="titleId">{{ o.title }}</h2>
        <p class="dialog__message">{{ o.message }}</p>
        <div class="dialog__actions">
          <button #cancel type="button" class="btn btn-ghost" (click)="answer(false)">
            {{ o.cancelLabel ?? 'Cancel' }}
          </button>
          <button
            type="button"
            class="btn"
            [class.btn-danger]="o.danger"
            [class.btn-primary]="!o.danger"
            (click)="answer(true)"
          >
            {{ o.confirmLabel ?? 'Confirm' }}
          </button>
        </div>
      }
    </dialog>
  `,
  styleUrl: '../dialog.css',
})
export class ConfirmDialog {
  protected readonly titleId = `confirm-dialog-title-${nextDialogId++}`;
  protected readonly options = signal<ConfirmOptions | null>(null);

  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly cancelButton = viewChild<ElementRef<HTMLButtonElement>>('cancel');
  private resolver: ((confirmed: boolean) => void) | null = null;

  /** Opens the dialog. Resolves true on confirm, false on cancel or Escape. Asking again cancels the open question. */
  ask(options: ConfirmOptions): Promise<boolean> {
    this.settle(false);
    this.options.set(options);
    // Render the content first so the safe choice (Cancel) exists and can take focus.
    this.changeDetector.detectChanges();
    return new Promise<boolean>((resolve) => {
      this.resolver = resolve;
      const dialog = this.dialog().nativeElement;
      if (!dialog.open) {
        dialog.showModal();
      }
      this.cancelButton()?.nativeElement.focus();
    });
  }

  protected answer(confirmed: boolean): void {
    this.settle(confirmed);
    this.dialog().nativeElement.close();
  }

  /** Fires for every way the dialog can close, including Escape. */
  protected onClosed(): void {
    if (this.dialog().nativeElement.open) {
      return; // A stale close event: the dialog was already reopened for the next question.
    }
    this.settle(false);
    this.options.set(null);
  }

  private settle(confirmed: boolean): void {
    this.resolver?.(confirmed);
    this.resolver = null;
  }
}
