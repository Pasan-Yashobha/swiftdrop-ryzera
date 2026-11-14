import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Board bar shared by the three pricing administration pages (same style as the Dispatcher Console bar). */
@Component({
  selector: 'app-pricing-admin-nav',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="board-bar" aria-label="Pricing administration">
      <span class="board-brand">Swift<span>Drop</span></span>
      <span class="board-tag">PRICING</span>
      <ul class="board-tabs">
        @for (tab of tabs; track tab.path) {
          <li>
            <a
              [routerLink]="tab.path"
              routerLinkActive="board-tab--active"
              ariaCurrentWhenActive="page"
              class="board-tab"
              >{{ tab.label }}</a
            >
          </li>
        }
      </ul>
    </nav>
  `,
  styles: `
    .board-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1.5rem;
      min-height: 56px;
      padding: 0 1.25rem;
      background: #121a17;
      border-bottom: 2px solid #000000;
      border-radius: 12px;
      box-shadow: 0 4px 12px rgb(0 0 0 / 15%);
    }
    .board-brand {
      font-family: 'IBM Plex Sans Condensed', 'Arial Narrow', sans-serif;
      font-size: 1.25rem;
      font-weight: 700;
      color: #ffffff;
    }
    .board-brand span {
      color: #ffc72c;
    }
    .board-tag {
      padding: 2px 8px;
      border: 1px solid #333333;
      border-radius: 4px;
      background: #000000;
      color: #ffffff;
      font-family: 'IBM Plex Mono', Consolas, monospace;
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.08em;
    }
    .board-tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 1.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .board-tab {
      position: relative;
      display: inline-flex;
      align-items: center;
      min-height: 56px;
      padding: 0 0.25rem;
      color: #a0aec0;
      font-weight: 500;
      text-decoration: none;
    }
    .board-tab:hover,
    .board-tab--active {
      color: #ffffff;
    }
    .board-tab--active {
      font-weight: 600;
    }
    .board-tab--active::after {
      content: '';
      position: absolute;
      right: 0;
      bottom: 0;
      left: 0;
      height: 3px;
      background: #ffc72c;
    }
    .board-tab:focus-visible {
      outline: none;
      box-shadow: inset 0 0 0 2px #ffc72c;
    }
  `,
})
export class PricingAdminNav {
  protected readonly tabs = [
    { path: '/admin/pricing/zones', label: 'Zones' },
    { path: '/admin/pricing/rate-cards', label: 'Rate cards' },
    { path: '/admin/pricing/promo-codes', label: 'Promo codes' },
  ];
}
