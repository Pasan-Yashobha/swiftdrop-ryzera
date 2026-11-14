import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { distinctUntilChanged, filter, map, skip } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { ADMIN_NAV } from './admin-nav';

/** Signed-in layout: header with the user and sign-out, left navigation (ADMIN_NAV) and the routed page. */
@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.css',
})
export class AdminShell {
  protected readonly auth = inject(AuthService);
  protected readonly sections = ADMIN_NAV;
  /** Mobile only: whether the navigation drawer is open. */
  protected readonly navOpen = signal(false);

  private readonly main = viewChild<ElementRef<HTMLElement>>('main');
  private readonly router = inject(Router);

  constructor() {
    // After each page change, close the mobile menu and move focus to the page so keyboard and screen
    // reader users start at the top. Query string changes (filters, tabs) keep focus where it is.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        map((event) => event.urlAfterRedirects.split(/[?#]/)[0]),
        distinctUntilChanged(),
        skip(1),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.navOpen.set(false);
        this.focusMain();
      });
  }

  protected toggleNav(): void {
    this.navOpen.update((open) => !open);
  }

  protected signOut(): void {
    this.auth.logout();
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
