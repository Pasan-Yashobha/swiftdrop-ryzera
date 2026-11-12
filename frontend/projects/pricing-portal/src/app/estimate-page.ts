import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PricingApi, Zone, Quote, EstimateInput, errorMessage } from './pricing-api';

@Component({
  selector: 'app-estimate',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  template: `
    <main class="estimate-layout">
      <section>
        <p class="eyebrow">DELIVERY FEE ESTIMATE</p>
        <h1>Where is it going?</h1>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        @if (loading()) {
          <p role="status">Loading delivery zones…</p>
        }
        <form #form="ngForm" (ngSubmit)="estimate()">
          <div class="panel addresses">
            <label
              ><b class="stop">A</b
              ><span
                >PICK UP FROM
                <select
                  name="origin"
                  [(ngModel)]="request.originZone"
                  (ngModelChange)="clearQuote()"
                  required
                >
                  <option value="">Choose origin zone</option>
                  @for (zone of zones(); track zone.id) {
                    <option [value]="zone.code">{{ zone.name }}</option>
                  }
                </select></span
              ></label
            >
            <p class="route-caption">
              {{ request.originZone || 'ORIGIN' }} →
              {{ request.destinationZone || 'DESTINATION' }} · {{ request.deliveryType }} RATE
            </p>
            <label
              ><b class="stop yellow">B</b
              ><span
                >DELIVER TO
                <select
                  name="destination"
                  [(ngModel)]="request.destinationZone"
                  (ngModelChange)="clearQuote()"
                  required
                >
                  <option value="">Choose destination zone</option>
                  @for (zone of zones(); track zone.id) {
                    <option [value]="zone.code">{{ zone.name }}</option>
                  }
                </select></span
              ></label
            >
          </div>
          <p class="eyebrow parcel-label">WHAT ARE YOU SENDING?</p>
          <div class="size-options">
            @for (size of sizes; track size.value) {
              <label [class.selected]="request.sizeClass === size.value">
                <input
                  type="radio"
                  name="size"
                  [value]="size.value"
                  [(ngModel)]="request.sizeClass"
                  (ngModelChange)="clearQuote()"
                />
                <strong>{{ size.label }}</strong
                ><span>{{ size.description }}</span>
              </label>
            }
          </div>
          <div class="weight-row">
            <label
              >WEIGHT (KG)<input
                name="weight"
                type="number"
                min="0.001"
                max="20"
                step="0.001"
                required
                [(ngModel)]="request.weightKg"
                (ngModelChange)="clearQuote()"
            /></label>
            <label
              >DELIVERY TYPE
              <select
                name="deliveryType"
                [(ngModel)]="request.deliveryType"
                (ngModelChange)="clearQuote()"
              >
                <option value="STANDARD">Standard</option>
                <option value="EXPRESS">Express</option>
              </select>
            </label>
          </div>
          <label class="promo-input"
            >PROMO CODE (OPTIONAL)
            <input
              name="promoCode"
              maxlength="30"
              pattern="[A-Za-z0-9_-]*"
              placeholder="e.g. SAVE10"
              [(ngModel)]="request.promoCode"
              (ngModelChange)="clearQuote()"
            />
          </label>
          <button class="primary" type="submit" [disabled]="form.invalid || busy() || loading()">
            {{ busy() ? 'Calculating…' : 'Calculate delivery fee' }}
          </button>
          @if (!loading() && zones().length === 0) {
            <p>No delivery zones are available yet. Ask an administrator to configure them.</p>
          }
        </form>
      </section>
      <aside class="waybill" aria-live="polite">
        <div class="waybill-top">
          <p class="eyebrow">WAYBILL · ESTIMATE</p>
          @if (quote(); as result) {
            <dl>
              <div>
                <dt>Base fare</dt>
                <dd>{{ result.amounts.baseAmount | currency: 'LKR' : '' : '1.2-2' }}</dd>
              </div>
              <div>
                <dt>Weight {{ request.weightKg }} kg</dt>
                <dd>{{ result.amounts.weightAmount | currency: 'LKR' : '' : '1.2-2' }}</dd>
              </div>
              <div>
                <dt>Parcel size</dt>
                <dd>{{ result.amounts.sizeAmount | currency: 'LKR' : '' : '1.2-2' }}</dd>
              </div>
              @if (request.deliveryType === 'EXPRESS') {
                <div>
                  <dt>Express surcharge</dt>
                  <dd>{{ result.amounts.expressAmount | currency: 'LKR' : '' : '1.2-2' }}</dd>
                </div>
              }
              @if (result.promoCode) {
                <div class="discount-line">
                  <dt>Promo · {{ result.promoCode }}</dt>
                  <dd>−{{ result.amounts.discountAmount | currency: 'LKR' : '' : '1.2-2' }}</dd>
                </div>
              }
            </dl>
          } @else {
            <p class="empty">Choose your route and parcel details to see the fare breakdown.</p>
          }
        </div>
        <div class="waybill-total">
          <div class="total-line">
            <span class="eyebrow">TOTAL (LKR)</span>
            <strong>{{
              quote() ? (quote()!.amounts.totalAmount | currency: 'LKR' : '' : '1.2-2') : '—'
            }}</strong>
          </div>
          @if (quote(); as result) {
            <p>Valid until {{ result.expiresAt | date: 'shortTime' }} · re-check when booking.</p>
            <p class="quote-ref">Quote {{ result.quoteId }}</p>
          } @else {
            <p>Your estimate will appear here.</p>
          }
        </div>
      </aside>
    </main>
  `,
})
export class EstimatePage implements OnInit {
  private readonly api = inject(PricingApi);
  readonly zones = signal<Zone[]>([]);
  readonly quote = signal<Quote | null>(null);
  readonly busy = signal(false);
  readonly loading = signal(true);
  readonly error = signal('');
  private revision = 0;
  request: EstimateInput = {
    originZone: '',
    destinationZone: '',
    weightKg: 2,
    sizeClass: 'MEDIUM',
    deliveryType: 'STANDARD',
  };
  readonly sizes = [
    { value: 'SMALL', label: 'Small', description: 'Compact parcels' },
    { value: 'MEDIUM', label: 'Box', description: 'Medium parcels' },
    { value: 'LARGE', label: 'Large', description: 'Bulky parcels' },
  ];

  async ngOnInit() {
    try {
      this.zones.set(await this.api.all<Zone>('/api/v1/pricing/zones'));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  clearQuote() {
    this.revision++;
    this.quote.set(null);
  }

  async estimate() {
    this.busy.set(true);
    this.error.set('');
    this.quote.set(null);
    const revision = this.revision;
    try {
      const quote = await this.api.save<Quote>('/api/v1/pricing/estimate', { ...this.request });
      if (revision === this.revision) this.quote.set(quote);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
