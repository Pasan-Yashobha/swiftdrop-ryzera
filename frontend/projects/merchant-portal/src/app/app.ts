import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { distinctUntilChanged, filter, map, skip, startWith } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { ChatWidget } from './features/assistant/chat-widget';
import { AppHeader } from './layout/app-header';
import { ToastOutlet } from './shared/ui/toast/toast-outlet';

/** Application shell: skip link, header, routed page, footer, the AI assistant and the toast region. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppHeader, ToastOutlet, ChatWidget],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly year = new Date().getFullYear();

  private readonly main = viewChild<ElementRef<HTMLElement>>('main');
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  /** The Assistant service serves customers and merchants only. */
  protected readonly canChat = computed(() => {
    const role = this.auth.role();
    return role === 'CUSTOMER' || role === 'MERCHANT';
  });

  /** True when displaying auth split screens (/login, /signup) to eliminate outer nav header & footer. */
  protected readonly isAuthRoute = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => this.checkIsAuth(event.urlAfterRedirects)),
      startWith(this.checkIsAuth(this.router.url)),
    ),
    { initialValue: this.checkIsAuth(this.router.url) },
  );

  private checkIsAuth(url: string): boolean {
    const clean = (url || '').split(/[?#]/)[0];
    return clean === '/login' || clean === '/signup' || clean === '/forgot-password' || clean === '/reset-password';
  }

  constructor() {
    // After each in-app page change, move focus to the page so keyboard and screen reader users start at the top.
    // Query and fragment changes stay on the same page (list filters, table of contents), so they keep focus.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        map((event) => event.urlAfterRedirects.split(/[?#]/)[0]),
        distinctUntilChanged(),
        skip(1),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.focusMain());
  }

  /** The skip link cannot be a plain #fragment link: with <base href="/"> it would reload the root route. */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.focusMain();
  }

  private focusMain(): void {
    this.main()?.nativeElement.focus({ preventScroll: true });
  }
}
