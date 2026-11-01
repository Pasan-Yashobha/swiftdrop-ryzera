import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Placeholder until the real page is built; keep the file name and class name when replacing it. */
@Component({
  selector: 'app-api-docs-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <h1>API documentation</h1>
      <p class="muted">This page is being built.</p>
    </div>
  `,
})
export class ApiDocsPage {}
