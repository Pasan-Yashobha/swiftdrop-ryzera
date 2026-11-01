import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CopyButton } from '../../../shared/ui/copy-button/copy-button';

/** A titled, copyable code sample. The `<pre>` is focusable so keyboard users can scroll long lines. */
@Component({
  selector: 'app-code-block',
  imports: [CopyButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="code">
      <figcaption class="code__bar">
        <span class="code__title">{{ title() }}</span>
        <app-copy-button [value]="code()" />
      </figcaption>
      <pre class="code__body" tabindex="0"><code>{{ code() }}</code></pre>
    </figure>
  `,
  styles: `
    .code {
      margin: 0;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--color-surface-alt);
      overflow: hidden;
    }
    .code__bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding: var(--space-1) var(--space-3);
      border-bottom: 1px solid var(--color-border);
      font-size: var(--text-sm);
      font-weight: 600;
    }
    .code__body {
      margin: 0;
      padding: var(--space-3) var(--space-4);
      overflow-x: auto;
      font-family: var(--font-mono);
      font-size: var(--text-sm);
      line-height: 1.5;
    }
  `,
})
export class CodeBlock {
  readonly title = input.required<string>();
  readonly code = input.required<string>();
}
