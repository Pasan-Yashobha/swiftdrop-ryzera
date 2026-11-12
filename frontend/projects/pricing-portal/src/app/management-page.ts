import { Component, inject, input, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PricingApi, Zone, ZoneInput, Rate, RateInput, errorMessage } from './pricing-api';

@Component({
  selector: 'app-management',
  imports: [FormsModule, CurrencyPipe],
  template: `
    <main class="management">
      <p class="eyebrow">DISPATCH / ADMINISTRATION</p>
      <h1>{{ view() === 'zones' ? 'Delivery zones' : 'Rate cards' }}</h1>
      <p class="subtitle">
        {{
          view() === 'zones'
            ? 'Define where SwiftDrop delivers.'
            : 'Set clear fares for every route.'
        }}
      </p>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      @if (notice()) {
        <p class="success" role="status">{{ notice() }}</p>
      }
      @if (loading()) {
        <p role="status">Loading configuration…</p>
      }
      @if (pending(); as item) {
        <section class="confirm panel" role="region" aria-label="Confirm deactivation">
          <p>Deactivate {{ item.label }}? Historical records will be retained.</p>
          <button class="danger" [disabled]="busy()" (click)="deactivate()">
            Confirm deactivation
          </button>
          <button class="secondary" [disabled]="busy()" (click)="pending.set(null)">Cancel</button>
        </section>
      }
      @if (view() === 'zones') {
        <div class="admin-layout">
          <section class="panel">
            <h2>{{ zoneId ? 'Edit zone' : 'Add a zone' }}</h2>
            <form #zoneForm="ngForm" (ngSubmit)="saveZone()">
              <label
                >ZONE CODE<input
                  name="code"
                  required
                  maxlength="30"
                  pattern="[A-Z][A-Z0-9_-]*"
                  placeholder="COLOMBO"
                  [(ngModel)]="zoneFormData.code"
              /></label>
              <small>Uppercase letters, numbers, underscores and hyphens.</small>
              <label
                >ZONE NAME<input
                  name="name"
                  required
                  maxlength="100"
                  pattern=".*\\S.*"
                  [(ngModel)]="zoneFormData.name"
              /></label>
              <label
                >DESCRIPTION<textarea
                  name="description"
                  maxlength="500"
                  [(ngModel)]="zoneFormData.description"
                ></textarea>
              </label>
              <button class="primary" [disabled]="zoneForm.invalid || busy() || loading()">
                {{ busy() ? 'Saving…' : 'Save zone' }}
              </button>
              @if (zoneId) {
                <button type="button" class="secondary" (click)="resetZone()">Cancel edit</button>
              }
            </form>
          </section>
          <section class="panel table-panel">
            <h2>
              Service areas <span class="count">{{ zones().length }}</span>
            </h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>ZONE</th>
                    <th>CODE</th>
                    <th>STATUS</th>
                    <th>ACTIONS</th>
                  </tr>
                </thead>
                <tbody>
                  @for (zone of zones(); track zone.id) {
                    <tr>
                      <td>{{ zone.name }}</td>
                      <td class="mono">{{ zone.code }}</td>
                      <td>
                        <span class="pill" [class.inactive]="!zone.active">{{
                          zone.active ? 'Active' : 'Inactive'
                        }}</span>
                      </td>
                      <td>
                        <button class="text-button" [disabled]="busy()" (click)="editZone(zone)">
                          Edit
                        </button>
                        @if (zone.active) {
                          <button
                            class="text-button"
                            [disabled]="busy()"
                            (click)="pending.set({ path: zonePath, id: zone.id, label: zone.name })"
                          >
                            Deactivate
                          </button>
                        }
                      </td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="4">No zones yet. Add your first service area.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        </div>
      } @else {
        <div class="admin-layout">
          <section class="panel">
            <h2>{{ rateId ? 'Edit rate card' : 'Add a rate card' }}</h2>
            <form #rateForm="ngForm" (ngSubmit)="saveRate()">
              <div class="form-grid">
                <label
                  >ORIGIN ZONE<select
                    name="origin"
                    required
                    [(ngModel)]="rateFormData.originZoneId"
                  >
                    <option value="">Choose zone</option>
                    @for (zone of zones(); track zone.id) {
                      @if (zone.active) {
                        <option [value]="zone.id">{{ zone.name }}</option>
                      }
                    }
                  </select></label
                >
                <label
                  >DESTINATION ZONE<select
                    name="destination"
                    required
                    [(ngModel)]="rateFormData.destinationZoneId"
                  >
                    <option value="">Choose zone</option>
                    @for (zone of zones(); track zone.id) {
                      @if (zone.active) {
                        <option [value]="zone.id">{{ zone.name }}</option>
                      }
                    }
                  </select></label
                >
                <label
                  >BASE FEE (LKR)<input
                    name="baseFee"
                    type="number"
                    required
                    min="0"
                    max="99999999.99"
                    step="0.01"
                    [(ngModel)]="rateFormData.baseFee"
                /></label>
                <label
                  >PER KG (LKR)<input
                    name="perKgFee"
                    type="number"
                    required
                    min="0"
                    max="99999999.99"
                    step="0.01"
                    [(ngModel)]="rateFormData.perKgFee"
                /></label>
                <label
                  >SMALL SURCHARGE<input
                    name="small"
                    type="number"
                    required
                    min="0"
                    max="99999999.99"
                    step="0.01"
                    [(ngModel)]="rateFormData.smallSurcharge"
                /></label>
                <label
                  >MEDIUM SURCHARGE<input
                    name="medium"
                    type="number"
                    required
                    min="0"
                    max="99999999.99"
                    step="0.01"
                    [(ngModel)]="rateFormData.mediumSurcharge"
                /></label>
                <label
                  >LARGE SURCHARGE<input
                    name="large"
                    type="number"
                    required
                    min="0"
                    max="99999999.99"
                    step="0.01"
                    [(ngModel)]="rateFormData.largeSurcharge"
                /></label>
                <label
                  >EXPRESS MULTIPLIER (OPTIONAL)<input
                    name="expressMultiplier"
                    type="number"
                    min="1"
                    max="999.99"
                    step="0.01"
                    placeholder="e.g. 1.50"
                    [(ngModel)]="rateFormData.expressMultiplier"
                /></label>
                <label
                  >VALID FROM<input
                    name="from"
                    type="date"
                    required
                    [(ngModel)]="rateFormData.validFrom"
                /></label>
                <label
                  >VALID UNTIL (EXCLUSIVE)<input
                    name="to"
                    type="date"
                    [(ngModel)]="rateFormData.validTo"
                /></label>
              </div>
              <p class="muted">Leave the end date empty for an ongoing rate.</p>
              <button class="primary" [disabled]="rateForm.invalid || busy() || loading()">
                {{ busy() ? 'Saving…' : 'Save rate card' }}
              </button>
              @if (rateId) {
                <button type="button" class="secondary" (click)="resetRate()">Cancel edit</button>
              }
            </form>
          </section>
          <section class="panel table-panel">
            <h2>
              Route fares <span class="count">{{ rates().length }}</span>
            </h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>ROUTE / DATES</th>
                    <th>BASE / KG</th>
                    <th>STATUS</th>
                    <th>ACTIONS</th>
                  </tr>
                </thead>
                <tbody>
                  @for (rate of rates(); track rate.id) {
                    <tr>
                      <td>
                        {{ zoneName(rate.originZoneId) }} → {{ zoneName(rate.destinationZoneId) }}
                        <small>{{ rate.validFrom }} → {{ rate.validTo || 'Ongoing' }}</small>
                      </td>
                      <td class="mono">
                        {{ rate.baseFee | currency: 'LKR' : '' : '1.2-2'
                        }}<small>+ {{ rate.perKgFee | currency: 'LKR' : '' : '1.2-2' }} / kg</small>
                      </td>
                      <td>
                        <span class="pill" [class.inactive]="!rate.active">{{
                          rate.active ? 'Enabled' : 'Inactive'
                        }}</span>
                      </td>
                      <td>
                        <button class="text-button" [disabled]="busy()" (click)="editRate(rate)">
                          Edit
                        </button>
                        @if (rate.active) {
                          <button
                            class="text-button"
                            [disabled]="busy()"
                            (click)="
                              pending.set({ path: ratePath, id: rate.id, label: 'this rate card' })
                            "
                          >
                            Deactivate
                          </button>
                        }
                      </td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="4">No rate cards yet. Configure a route to enable estimates.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        </div>
      }
    </main>
  `,
})
export class ManagementPage implements OnInit {
  readonly view = input.required<'zones' | 'rates'>();
  private readonly api = inject(PricingApi);
  readonly zonePath = '/api/v1/admin/zones';
  readonly ratePath = '/api/v1/admin/rate-cards';
  readonly zones = signal<Zone[]>([]);
  readonly rates = signal<Rate[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly pending = signal<{ path: string; id: string; label: string } | null>(null);
  zoneId = '';
  rateId = '';
  zoneFormData: ZoneInput = { code: '', name: '', description: '' };
  rateFormData = this.newRate();

  private newRate(): RateInput {
    return {
      originZoneId: '',
      destinationZoneId: '',
      baseFee: 0,
      perKgFee: 0,
      smallSurcharge: 0,
      mediumSurcharge: 0,
      largeSurcharge: 0,
      expressMultiplier: null,
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
    const [zones, rates] = await Promise.all([
      this.api.all<Zone>(this.zonePath),
      this.api.all<Rate>(this.ratePath),
    ]);
    this.zones.set(zones);
    this.rates.set(rates);
  }

  resetZone() {
    this.zoneId = '';
    this.zoneFormData = { code: '', name: '', description: '' };
  }
  resetRate() {
    this.rateId = '';
    this.rateFormData = this.newRate();
  }
  editZone(zone: Zone) {
    this.zoneId = zone.id;
    this.zoneFormData = { code: zone.code, name: zone.name, description: zone.description || '' };
    this.notice.set('');
  }
  editRate(rate: Rate) {
    this.rateId = rate.id;
    this.rateFormData = { ...rate };
    this.notice.set('');
  }
  zoneName(id: string) {
    return this.zones().find((zone) => zone.id === id)?.name || 'Unknown zone';
  }

  async saveZone() {
    await this.mutate(async () => {
      await this.api.save(this.zonePath, this.zoneFormData, this.zoneId);
      this.resetZone();
    }, 'Zone saved.');
  }

  async saveRate() {
    if (this.rateFormData.validTo && this.rateFormData.validTo <= this.rateFormData.validFrom) {
      this.error.set('The end date must be after the start date.');
      return;
    }
    await this.mutate(async () => {
      await this.api.save(
        this.ratePath,
        { ...this.rateFormData, validTo: this.rateFormData.validTo || null },
        this.rateId,
      );
      this.resetRate();
    }, 'Rate card saved.');
  }

  async deactivate() {
    const item = this.pending();
    if (!item) return;
    await this.mutate(async () => {
      await this.api.deactivate(item.path, item.id);
      this.pending.set(null);
      this.resetZone();
      this.resetRate();
    }, 'Deactivated. Historical records retained.');
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
