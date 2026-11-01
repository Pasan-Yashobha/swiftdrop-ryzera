import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';

type CopyState = 'idle' | 'copied' | 'failed';

const FEEDBACK_MS = 2000;

/** Copies `value` to the clipboard and tells the user (and screen readers) whether it worked. */
@Component({
  selector: 'app-copy-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="btn btn-ghost btn-sm" (click)="copy()">
      @switch (state()) {
        @case ('copied') {
          Copied
        }
        @case ('failed') {
          Copy failed
        }
        @default {
          {{ label() }}
        }
      }
    </button>
    <span class="visually-hidden" role="status">
      @if (state() === 'copied') {
        Copied to clipboard
      } @else if (state() === 'failed') {
        Copy failed, select the text and copy it manually
      }
    </span>
  `,
})
export class CopyButton {
  readonly value = input.required<string>();
  readonly label = input('Copy');

  protected readonly state = signal<CopyState>('idle');
  private resetTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.resetTimer));
  }

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.value());
      this.show('copied');
    } catch {
      this.show('failed');
    }
  }

  private show(state: CopyState): void {
    this.state.set(state);
    clearTimeout(this.resetTimer);
    this.resetTimer = setTimeout(() => this.state.set('idle'), FEEDBACK_MS);
  }
}
