import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  inject,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';
import { CopyButton } from '../copy-button/copy-button';

export interface SecretRequest {
  title: string;
  /** What the secret is, in lower case: "API key", "client secret", "signing secret". */
  label: string;
  secret: string;
  description?: string;
}

let nextDialogId = 0;

/**
 * Shows a credential exactly once. The dialog cannot be dismissed (Escape is ignored) until the user
 * ticks "I have saved it" and presses Done; afterwards the secret is dropped from memory and the DOM.
 * Keep one in the template and `await dialog.reveal({...})`.
 */
@Component({
  selector: 'app-secret-once-dialog',
  imports: [CopyButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="dialog"
      [attr.aria-labelledby]="titleId"
      (cancel)="$event.preventDefault()"
      (close)="onClosed()"
    >
      @if (request(); as r) {
        <h2 [id]="titleId">{{ r.title }}</h2>
        @if (r.description) {
          <p class="dialog__message">{{ r.description }}</p>
        }
        <p class="alert alert--warning">
          This is the only time the {{ r.label }} is shown. Copy it now and store it somewhere safe.
        </p>
        <div class="secret">
          <code class="secret__value" tabindex="0" [attr.aria-label]="r.label">{{ r.secret }}</code>
          <app-copy-button [value]="r.secret" />
        </div>
        <label class="checkbox">
          <input
            #ack
            type="checkbox"
            [checked]="acknowledged()"
            (change)="acknowledged.set(ack.checked)"
          />
          I have saved the {{ r.label }}
        </label>
        <div class="dialog__actions">
          <button
            type="button"
            class="btn btn-primary"
            [disabled]="!acknowledged()"
            (click)="finish()"
          >
            Done
          </button>
        </div>
      }
    </dialog>
  `,
  styleUrl: '../dialog.css',
  styles: `
    .secret {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-3);
    }
    .secret__value {
      flex: 1 1 14rem;
      padding: var(--space-3);
      border: 1px solid var(--color-border-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface-alt);
      overflow-wrap: anywhere;
      user-select: all;
    }
  `,
})
export class SecretOnceDialog implements OnDestroy {
  protected readonly titleId = `secret-dialog-title-${nextDialogId++}`;
  protected readonly request = signal<SecretRequest | null>(null);
  protected readonly acknowledged = signal(false);

  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private resolver: (() => void) | null = null;

  /** Opens the dialog. Resolves once the user has acknowledged saving the secret and closed it. */
  reveal(request: SecretRequest): Promise<void> {
    if (this.resolver) {
      return Promise.reject(new Error('A secret is already being shown'));
    }
    this.acknowledged.set(false);
    this.request.set(request);
    this.changeDetector.detectChanges();
    return new Promise<void>((resolve) => {
      this.resolver = resolve;
      this.dialog().nativeElement.showModal();
    });
  }

  ngOnDestroy(): void {
    this.clear();
  }

  protected finish(): void {
    this.clear();
    this.dialog().nativeElement.close();
  }

  /**
   * Escape cannot be suppressed forever (browsers close a dialog on a repeated Escape), so an early close
   * is undone: the secret stays up until the user acknowledges it.
   */
  protected onClosed(): void {
    if (this.resolver && !this.dialog().nativeElement.open) {
      this.dialog().nativeElement.showModal();
    }
  }

  private clear(): void {
    this.request.set(null);
    this.acknowledged.set(false);
    this.resolver?.();
    this.resolver = null;
  }
}
