import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-merchants-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card p-4">
      <p class="rb-eyebrow">Administration</p>
      <h1>Merchant Management</h1>
      <p class="page__lead">Merchant approval and management workflows.</p>
    </div>
  `,
  styleUrls: ['../route-board.css'],
})
export class MerchantsPage {}
