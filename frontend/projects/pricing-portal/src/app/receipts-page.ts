import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../environment';
import { PricingApi, Receipt, ReceiptSnapshot, errorMessage } from './pricing-api';

const DOCUMENT_HINT = 'the Document Service is running on port 8087';

/**
 * Lists delivery receipts from the Document Service and downloads their PDFs.
 * Customers only ever receive their own receipts; Admin receives all of them.
 * That rule is enforced by the backend, not by this component.
 */
@Component({
  selector: 'app-receipts',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  template: `
    <main class="management">
      <p class="eyebrow">{{ admin ? 'DISPATCH / ADMINISTRATION' : 'MY DELIVERIES' }}</p>
      <h1>Receipts</h1>
      <p class="subtitle">
        {{
          admin
            ? 'Every delivery receipt issued by SwiftDrop.'
            : 'Download a PDF receipt for each delivered parcel.'
        }}
      </p>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      @if (notice()) {
        <p class="success" role="status">{{ notice() }}</p>
      }
      @if (loading()) {
        <p role="status">Loading receipts…</p>
      }
      <div [class.admin-layout]="showGenerator">
        @if (showGenerator) {
          <section class="panel">
            <h2>Create test receipt</h2>
            <p class="notice">
              Development only. Stands in for the parcel.delivered event until the Parcel Service
              publishes it.
            </p>
            <form #form="ngForm" (ngSubmit)="generate()">
              <label
                >CUSTOMER ID (JWT SUBJECT)<input
                  name="customer"
                  required
                  maxlength="255"
                  [(ngModel)]="snapshot.customerId"
              /></label>
              <div class="form-grid">
                <label
                  >TRACKING NUMBER<input
                    name="tracking"
                    required
                    maxlength="60"
                    pattern="[A-Za-z0-9_-]+"
                    [(ngModel)]="snapshot.trackingNumber"
                /></label>
                <label
                  >DELIVERED AT<input
                    name="delivered"
                    type="datetime-local"
                    required
                    [max]="nowLocal()"
                    [(ngModel)]="deliveredLocal"
                /></label>
                @for (field of amountFields; track field.key) {
                  <label
                    >{{ field.label }}<input
                      [name]="field.key"
                      type="number"
                      required
                      min="0"
                      step="0.01"
                      [(ngModel)]="snapshot[field.key]"
                  /></label>
                }
              </div>
              <p class="muted">
                Total {{ total() | currency: 'LKR' : 'symbol' : '1.2-2' }} = charges − discount.
              </p>
              <button class="primary" [disabled]="form.invalid || total() < 0 || busy()">
                {{ busy() ? 'Generating…' : 'Generate receipt' }}
              </button>
            </form>
          </section>
        }
        <section class="panel table-panel">
          <h2>
            {{ admin ? 'All receipts' : 'Your receipts' }}
            <span class="count">{{ receipts().length }}</span>
          </h2>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>RECEIPT / TRACKING</th>
                  <th>DELIVERED</th>
                  <th>TOTAL</th>
                  <th>PDF</th>
                </tr>
              </thead>
              <tbody>
                @for (receipt of receipts(); track receipt.id) {
                  <tr>
                    <td>
                      <strong class="mono">{{ receipt.receiptNo }}</strong
                      ><small class="mono">{{ receipt.trackingNumber }}</small>
                    </td>
                    <td>
                      {{ receipt.deliveredAt | date: 'd MMM y, HH:mm' : '+0530' }}
                      <small>Sri Lanka time</small>
                    </td>
                    <td>
                      {{ receipt.totalAmount | currency: receipt.currency : 'symbol' : '1.2-2' }}
                    </td>
                    <td>
                      <button
                        class="text-button"
                        [disabled]="downloading() === receipt.id"
                        (click)="download(receipt)"
                      >
                        {{ downloading() === receipt.id ? 'Downloading…' : 'Download' }}
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="4">
                      {{
                        loading()
                          ? 'Loading…'
                          : 'No receipts yet. A receipt appears after a parcel is delivered.'
                      }}
                    </td>
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
export class ReceiptsPage implements OnInit {
  private readonly api = inject(PricingApi);
  private readonly path = '/api/v1/receipts';
  readonly admin = this.api.role() === 'ADMIN';
  readonly showGenerator = this.admin && environment.development;
  readonly receipts = signal<Receipt[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly downloading = signal('');
  readonly error = signal('');
  readonly notice = signal('');

  readonly amountFields: { key: AmountKey; label: string }[] = [
    { key: 'baseAmount', label: 'BASE (LKR)' },
    { key: 'weightAmount', label: 'WEIGHT (LKR)' },
    { key: 'sizeAmount', label: 'SIZE (LKR)' },
    { key: 'expressAmount', label: 'EXPRESS (LKR)' },
    { key: 'discountAmount', label: 'DISCOUNT (LKR)' },
  ];
  snapshot = this.sampleSnapshot();
  deliveredLocal = this.nowLocal();

  async ngOnInit() {
    try {
      await this.reload();
    } catch (error) {
      this.error.set(errorMessage(error, DOCUMENT_HINT));
    } finally {
      this.loading.set(false);
    }
  }

  private async reload() {
    this.receipts.set(await this.api.all<Receipt>(this.path));
  }

  /** Total is derived, never typed, so it always matches the server's consistency rule. */
  total(): number {
    const s = this.snapshot;
    const cents =
      Math.round(s.baseAmount * 100) +
      Math.round(s.weightAmount * 100) +
      Math.round(s.sizeAmount * 100) +
      Math.round(s.expressAmount * 100) -
      Math.round(s.discountAmount * 100);
    return cents / 100;
  }

  nowLocal(): string {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 16);
  }

  async generate() {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    try {
      const body: ReceiptSnapshot = {
        ...this.snapshot,
        deliveredAt: new Date(this.deliveredLocal).toISOString(),
        totalAmount: this.total(),
      };
      const receipt = await this.api.save<Receipt>('/api/v1/dev/receipts', body);
      this.notice.set(`Receipt ${receipt.receiptNo} generated for ${body.customerId}.`);
      this.snapshot = this.sampleSnapshot();
      await this.reload();
    } catch (error) {
      this.error.set(errorMessage(error, DOCUMENT_HINT));
    } finally {
      this.busy.set(false);
    }
  }

  /** Turns the authenticated PDF bytes into a temporary local file the browser can save. */
  async download(receipt: Receipt) {
    this.downloading.set(receipt.id);
    this.error.set('');
    try {
      const pdf = await this.api.downloadReceipt(receipt.id);
      const url = URL.createObjectURL(pdf);
      const link = document.createElement('a');
      link.href = url;
      link.download = `receipt-${receipt.receiptNo}.pdf`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      this.error.set(errorMessage(error, DOCUMENT_HINT));
    } finally {
      this.downloading.set('');
    }
  }

  private sampleSnapshot(): Omit<ReceiptSnapshot, 'deliveredAt' | 'totalAmount'> {
    return {
      parcelId: crypto.randomUUID(),
      customerId: 'pricing-demo-customer',
      trackingNumber: `SD-${Math.floor(1000 + Math.random() * 9000)}-${Math.floor(1000 + Math.random() * 9000)}`,
      baseAmount: 300,
      weightAmount: 200,
      sizeAmount: 50,
      expressAmount: 137.5,
      discountAmount: 68.75,
    };
  }
}

type AmountKey = 'baseAmount' | 'weightAmount' | 'sizeAmount' | 'expressAmount' | 'discountAmount';
