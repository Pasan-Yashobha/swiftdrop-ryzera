import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { MerchantStore } from '../core/merchant/merchant.store';
import { StatusBadge } from '../shared/ui/status-badge/status-badge';

interface NavLink {
  path: string;
  label: string;
}

const GUEST_LINKS: readonly NavLink[] = [
  { path: '/login', label: 'Sign in' },
  { path: '/signup', label: 'Create account' },
];

const APPROVED_LINKS: readonly NavLink[] = [
  { path: '/dashboard', label: 'Dashboard' },
  { path: '/orders', label: 'Orders' },
  { path: '/developers/keys', label: 'API keys' },
  { path: '/developers/webhooks', label: 'Webhooks' },
  { path: '/developers/docs', label: 'API docs' },
  { path: '/profile', label: 'Profile' },
];

const UNAPPROVED_LINKS: readonly NavLink[] = [
  { path: '/status', label: 'Application status' },
  { path: '/profile', label: 'Profile' },
];

const ONBOARDING_LINKS: readonly NavLink[] = [
  { path: '/onboarding', label: 'Register your business' },
];

/** Brand, role / merchant-status chip and the navigation, which collapses behind a menu button on phones. */
@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-header.html',
  styleUrl: './app-header.css',
})
export class AppHeader {
  protected readonly auth = inject(AuthService);
  protected readonly store = inject(MerchantStore);

  protected readonly menuOpen = signal(false);

  protected readonly links = computed<readonly NavLink[]>(() => {
    if (!this.auth.isAuthenticated()) {
      return GUEST_LINKS;
    }
    const status = this.store.status();
    if (status === null) {
      return ONBOARDING_LINKS;
    }
    return status === 'APPROVED' ? APPROVED_LINKS : UNAPPROVED_LINKS;
  });

  constructor() {
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected logout(): void {
    this.auth.logout();
  }
}
