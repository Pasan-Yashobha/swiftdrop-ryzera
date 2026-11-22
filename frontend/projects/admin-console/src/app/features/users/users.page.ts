import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-users-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card p-4">
      <p class="rb-eyebrow">Administration</p>
      <h1>User Management</h1>
      <p class="page__lead">User accounts and role administration.</p>
    </div>
  `,
  styleUrls: ['../route-board.css'],
})
export class UsersPage {}
