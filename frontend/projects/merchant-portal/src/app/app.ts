import { ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { distinctUntilChanged, filter, map, skip } from 'rxjs';
import { AppHeader } from './layout/app-header';
import { ToastOutlet } from './shared/ui/toast/toast-outlet';

/** Application shell: skip link, header, routed page, footer and the toast region. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppHeader, ToastOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly year = new Date().getFullYear();

  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  constructor() {
    // After each in-app page change, move focus to the page so keyboard and screen reader users start at the top.
    // Query and fragment changes stay on the same page (list filters, table of contents), so they keep focus.
    inject(Router)
      .events.pipe(
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
    this.main().nativeElement.focus({ preventScroll: true });
  }
}
