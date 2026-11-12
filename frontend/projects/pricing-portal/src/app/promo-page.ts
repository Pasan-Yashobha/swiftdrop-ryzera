import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PricingApi, Promo, PromoInput, errorMessage } from './pricing-api';

@Component({
  selector: 'app-promos',
  imports: [FormsModule, CurrencyPipe],
  template: `
    <main class="management">
      <p class="eyebrow">DISPATCH / ADMINISTRATION</p>
      <h1>Promo codes</h1>
      <p class="subtitle">Give customers a reason to send their next parcel.</p>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      @if (notice()) {
        <p class="success" role="status">{{ notice() }}</p>
      }
      @if (loading()) {
        <p role="status">Loading promo codes…</p>
      }
      @if (pending(); as promo) {
        <section class="confirm panel" aria-label="Confirm promo deactivation">
          <p>Deactivate {{ promo.code }}?</p>
          <button class="danger" [disabled]="busy()" (click)="deactivate()">
            Confirm deactivation
          </button>
          <button class="secondary" [disabled]="busy()" (click)="pending.set(null)">Cancel</button>
        </section>
      }
      <div class="admin-layout">
        <section class="panel">
          <h2>{{ id ? 'Edit promo code' : 'Add a promo code' }}</h2>
          <form #form="ngForm" (ngSubmit)="save()">
            <label
              >PROMO CODE<input
                name="code"
                required
                maxlength="30"
                pattern="[A-Za-z0-9_-]+"
                [(ngModel)]="data.code"
                placeholder="SAVE10"
            /></label>
            <div class="form-grid">
              <label
                >DISCOUNT TYPE<select name="type" [(ngModel)]="data.discountType">
                  <option value="PERCENT">Percentage</option>
                  <option value="FLAT">Fixed amount (LKR)</option>
                </select></label
              >
              <label
                >DISCOUNT VALUE<input
                  name="value"
                  type="number"
                  required
                  min="0.01"
                  [max]="data.discountType === 'PERCENT' ? 100 : 99999999.99"
                  step="0.01"
                  [(ngModel)]="data.discountValue"
              /></label>
              <label
                >MINIMUM FARE (LKR)<input
                  name="minimum"
                  type="number"
                  required
                  min="0"
                  step="0.01"
                  [(ngModel)]="data.minOrderValue"
              /></label>
              <label
                >MAXIMUM USES (OPTIONAL)<input
                  name="maxUses"
                  type="number"
                  min="1"
                  max="2147483647"
                  step="1"
                  [(ngModel)]="data.maxUses"
              /></label>
              <label
                >VALID FROM<input name="from" type="date" required [(ngModel)]="data.validFrom"
              /></label>
              <label
                >VALID UNTIL (EXCLUSIVE)<input name="until" type="date" [(ngModel)]="data.validTo"
              /></label>
            </div>
            <p class="muted">
              Empty maximum uses and end date mean unlimited. Price previews do not redeem codes.
            </p>
            <button class="primary" [disabled]="form.invalid || busy() || loading()">
              {{ busy() ? 'Saving…' : 'Save promo code' }}
            </button>
            @if (id) {
              <button class="secondary" type="button" (click)="reset()">Cancel edit</button>
            }
          </form>
        </section>
        <section class="panel table-panel">
          <h2>
            Available promotions <span class="count">{{ promos().length }}</span>
          </h2>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>CODE / DATES</th>
                  <th>DISCOUNT</th>
                  <th>STATUS / USES</th>
                  <th>ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                @for (promo of promos(); track promo.id) {
                  <tr>
                    <td>
                      <strong class="mono">{{ promo.code }}</strong
                      ><small>{{ promo.validFrom }} → {{ promo.validTo || 'Ongoing' }}</small>
                    </td>
                    <td>
                      @if (promo.discountType === 'PERCENT') {
                        {{ promo.discountValue }}%
                      } @else {
                        {{ promo.discountValue | currency: 'LKR' : 'symbol' : '1.2-2' }}
                      }
                      <small
                        >Minimum
                        {{ promo.minOrderValue | currency: 'LKR' : 'symbol' : '1.2-2' }}</small
                      >
                    </td>
                    <td>
                      <span class="pill" [class.inactive]="!promo.active">{{
                        promo.active ? 'Enabled' : 'Inactive'
                      }}</span>
                      <small>{{ promo.usedCount }} / {{ promo.maxUses ?? 'Unlimited' }}</small>
                    </td>
                    <td>
                      <button class="text-button" [disabled]="busy()" (click)="edit(promo)">
                        Edit
                      </button>
                      @if (promo.active) {
                        <button
                          class="text-button"
                          [disabled]="busy()"
                          (click)="pending.set(promo)"
                        >
                          Deactivate
                        </button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="4">No promotions yet. Add your first code.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>
  `,
})
export class PromoPage implements OnInit {
  private readonly api = inject(PricingApi);
  private readonly path = '/api/v1/admin/promo-codes';
  readonly promos = signal<Promo[]>([]);
  readonly busy = signal(false);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly pending = signal<Promo | null>(null);
  id = '';
  data = this.empty();

  private empty(): PromoInput {
    return {
      code: '',
      discountType: 'PERCENT',
      discountValue: 10,
      minOrderValue: 0,
      maxUses: null,
      validFrom: '',
      validTo: null,
    };
  }

  async ngOnInit() {
    try {
      await this.reload();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  private async reload() {
    this.promos.set(await this.api.all<Promo>(this.path));
  }
  reset() {
    this.id = '';
    this.data = this.empty();
  }
  edit(promo: Promo) {
    this.id = promo.id;
    this.data = { ...promo };
    this.notice.set('');
  }

  async save() {
    if (this.data.validTo && this.data.validTo <= this.data.validFrom) {
      this.error.set('The end date must be after the start date.');
      return;
    }
    await this.mutate(async () => {
      await this.api.save(this.path, { ...this.data, validTo: this.data.validTo || null }, this.id);
      this.reset();
    }, 'Promo code saved.');
  }

  async deactivate() {
    const promo = this.pending();
    if (!promo) return;
    await this.mutate(async () => {
      await this.api.deactivate(this.path, promo.id);
      this.pending.set(null);
      this.reset();
    }, 'Promo code deactivated.');
  }

  private async mutate(action: () => Promise<void>, message: string) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    try {
      await action();
      await this.reload();
      this.notice.set(message);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
