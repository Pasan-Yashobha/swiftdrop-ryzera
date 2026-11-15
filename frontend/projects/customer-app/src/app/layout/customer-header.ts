import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';

interface NavLink {
  path: string;
  label: string;
}

const NAV_LINKS: readonly NavLink[] = [
  { path: '/dashboard', label: 'Dashboard' },
  { path: '/book', label: 'Book Delivery' },
  { path: '/history', label: 'My Deliveries' },
  { path: '/track', label: 'Track Parcel' },
];

@Component({
  selector: 'app-customer-header',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-header.html',
  styleUrl: './customer-header.css',
})
export class CustomerHeader {
  protected readonly menuOpen = signal(false);
  protected readonly links = signal(NAV_LINKS);

  constructor() {
    inject(Router)
      .events.pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }
}
