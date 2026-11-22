import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastOutlet } from './shared/ui/toast/toast-outlet';

/** Root: the routed page (login, or the admin shell with its children) and the toast live region. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <app-toast-outlet />
  `,
})
export class App {}
