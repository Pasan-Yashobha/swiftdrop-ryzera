import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../environment';
import { PricingApi, errorMessage } from './pricing-api';
import { EstimatePage } from './estimate-page';
import { ManagementPage } from './management-page';
import { PromoPage } from './promo-page';
import { ReceiptsPage } from './receipts-page';
import { InvoicesPage } from './invoices-page';
import { ChatWidget } from './chat-widget';

type Tab = 'estimate' | 'zones' | 'rates' | 'promos' | 'receipts' | 'invoices';

@Component({
  selector: 'app-root',
  imports: [
    FormsModule,
    EstimatePage,
    ManagementPage,
    PromoPage,
    ReceiptsPage,
    InvoicesPage,
    ChatWidget,
  ],
  template: `
    <header>
      <a class="brand" href="/">Swift<span>Drop</span></a>
      @if (api.token()) {
        <nav aria-label="Pricing navigation">
          @if (api.role() === 'MERCHANT') {
            <button [class.current]="tab() !== 'invoices'" (click)="tab.set('estimate')">
              Fee estimate
            </button>
            <button [class.current]="tab() === 'invoices'" (click)="tab.set('invoices')">
              Invoices
            </button>
          } @else if (api.role() !== 'ADMIN') {
            <button [class.current]="tab() !== 'receipts'" (click)="tab.set('estimate')">
              Fee estimate
            </button>
            <button [class.current]="tab() === 'receipts'" (click)="tab.set('receipts')">
              Receipts
            </button>
          } @else {
            <button [class.current]="tab() === 'zones'" (click)="tab.set('zones')">Zones</button>
            <button [class.current]="tab() === 'rates'" (click)="tab.set('rates')">
              Rate cards
            </button>
            <button [class.current]="tab() === 'promos'" (click)="tab.set('promos')">
              Promo codes
            </button>
            <button [class.current]="tab() === 'receipts'" (click)="tab.set('receipts')">
              Receipts
            </button>
            <button [class.current]="tab() === 'invoices'" (click)="tab.set('invoices')">
              Invoices
            </button>
          }
        </nav>
        <div class="account">
          <span class="eyebrow">{{ api.role() }}</span>
          <button class="quiet" (click)="signOut()">Sign out</button>
        </div>
      }
    </header>
    @if (!api.token()) {
      <main class="connect-layout">
        <section>
          <p class="eyebrow">PRICING & ZONES</p>
          <h1>Every parcel.<br />Know the fare.</h1>
          <p>Configure delivery rates and get a clear estimate before booking.</p>
          <div class="route-stops" aria-hidden="true"><b>A</b><span></span><b>B</b></div>
        </section>
        <section class="panel">
          <p class="eyebrow">CONNECT TO SWIFTDROP</p>
          <h2>Welcome back</h2>
          <label
            >Account role<select [(ngModel)]="role">
              <option value="CUSTOMER">Customer</option>
              <option value="MERCHANT">Merchant</option>
              <option value="ADMIN">Administrator</option>
            </select></label
          >
          @if (development) {
            <p class="notice">Local development access · tokens last one hour</p>
            <button class="primary wide" [disabled]="busy()" (click)="connect()">
              {{ busy() ? 'Connecting…' : 'Connect development account' }}
            </button>
          }
          <details>
            <summary>Use an existing access token</summary>
            <label
              >Bearer token<input type="password" autocomplete="off" [(ngModel)]="accessToken"
            /></label>
            <button class="secondary" [disabled]="!accessToken.trim()" (click)="connectToken()">
              Connect token
            </button>
          </details>
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }
        </section>
      </main>
    } @else if (tab() === 'receipts' && api.role() !== 'MERCHANT') {
      <app-receipts />
    } @else if (tab() === 'invoices' && (api.role() === 'MERCHANT' || api.role() === 'ADMIN')) {
      <app-invoices />
    } @else if (api.role() === 'ADMIN') {
      @if (tab() === 'promos') {
        <app-promos />
      } @else {
        <app-management [view]="tab() === 'rates' ? 'rates' : 'zones'" />
      }
    } @else {
      <app-estimate />
    }
    @if (api.token() && api.role() !== 'ADMIN') {
      <app-chat-widget />
    }
  `,
})
export class App {
  readonly api = inject(PricingApi);
  readonly development = environment.development;
  readonly tab = signal<Tab>('estimate');
  readonly busy = signal(false);
  readonly error = signal('');
  role = 'CUSTOMER';
  accessToken = '';

  async connect() {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.connectDevelopment(this.role);
      this.openHome();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  connectToken() {
    this.api.role.set(this.role);
    this.api.token.set(this.accessToken.trim());
    this.accessToken = '';
    this.openHome();
  }

  signOut() {
    this.api.token.set('');
    this.tab.set('estimate');
  }

  /** Each role starts on its main screen: Admin on Zones, everyone else on the estimator. */
  private openHome() {
    this.tab.set(this.api.role() === 'ADMIN' ? 'zones' : 'estimate');
  }
}
