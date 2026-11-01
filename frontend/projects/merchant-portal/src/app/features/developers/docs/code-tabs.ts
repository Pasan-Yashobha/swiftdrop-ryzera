import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  signal,
  viewChildren,
} from '@angular/core';
import { CodeBlock } from '../shared/code-block';
import { CodeSample } from './api-docs.samples';

let nextTabsId = 0;

/**
 * Language tabs for a code sample, following the WAI-ARIA tabs pattern: one tab stop for the tab list,
 * arrow keys / Home / End move between tabs and select them.
 */
@Component({
  selector: 'app-code-tabs',
  imports: [CodeBlock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tabs">
      <div role="tablist" class="tabs__list" [attr.aria-label]="label()">
        @for (sample of samples(); track sample.id; let index = $index) {
          <button
            #tab
            type="button"
            role="tab"
            class="tabs__tab"
            [id]="tabId(sample)"
            [attr.aria-selected]="index === selectedIndex()"
            [attr.aria-controls]="panelId(sample)"
            [tabindex]="index === selectedIndex() ? 0 : -1"
            (click)="select(index)"
            (keydown)="onKeydown($event)"
          >
            {{ sample.label }}
          </button>
        }
      </div>
      @for (sample of samples(); track sample.id; let index = $index) {
        <div
          role="tabpanel"
          [id]="panelId(sample)"
          [attr.aria-labelledby]="tabId(sample)"
          [hidden]="index !== selectedIndex()"
        >
          <app-code-block [title]="sample.label + ' example'" [code]="sample.code" />
        </div>
      }
    </div>
  `,
  styles: `
    .tabs__list {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-1);
      margin-bottom: var(--space-2);
    }
    .tabs__tab {
      padding: var(--space-2) var(--space-4);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
      color: var(--color-text);
      font: inherit;
      font-weight: 600;
      cursor: pointer;
    }
    .tabs__tab[aria-selected='true'] {
      border-color: var(--color-primary);
      background: var(--color-primary);
      color: var(--color-on-primary);
    }
  `,
})
export class CodeTabs {
  readonly samples = input.required<readonly CodeSample[]>();
  /** Accessible name of the tab list, for example "Signature verification language". */
  readonly label = input.required<string>();

  protected readonly selectedIndex = signal(0);

  private readonly idPrefix = `code-tabs-${nextTabsId++}`;
  private readonly tabs = viewChildren<ElementRef<HTMLButtonElement>>('tab');

  protected tabId(sample: CodeSample): string {
    return `${this.idPrefix}-tab-${sample.id}`;
  }

  protected panelId(sample: CodeSample): string {
    return `${this.idPrefix}-panel-${sample.id}`;
  }

  protected select(index: number): void {
    this.selectedIndex.set(index);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const target = this.targetIndex(event.key);
    if (target === null) {
      return;
    }
    event.preventDefault();
    this.select(target);
    this.tabs()[target].nativeElement.focus();
  }

  private targetIndex(key: string): number | null {
    const count = this.samples().length;
    const current = this.selectedIndex();
    switch (key) {
      case 'ArrowRight':
        return (current + 1) % count;
      case 'ArrowLeft':
        return (current - 1 + count) % count;
      case 'Home':
        return 0;
      case 'End':
        return count - 1;
      default:
        return null;
    }
  }
}
