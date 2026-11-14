import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../environment';
import { Invoice, InvoiceDetail, InvoiceInput, PricingApi, errorMessage } from './pricing-api';

const DOCUMENT_HINT = 'the Document Service is running on port 8092';
const MAX_FORM_LINES = 50;

interface DraftLine {
  key: number;
  parcelId: string;
  trackingNumber: string;
  description: string;
  amount: number | null;
}

/**
 * Lists merchant invoices from the Document Service, shows an invoice's billing lines and
 * downloads the PDF. A merchant only ever receives its own invoices; Admin receives all of them.
 * That rule is enforced by the backend, not by this component.
 */
@Component({
  selector: 'app-invoices',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  template: `
    <main class="management">
      <p class="eyebrow">{{ admin ? 'DISPATCH / ADMINISTRATION' : 'MY BUSINESS' }}</p>
      <h1>Invoices</h1>
      <p class="subtitle">
        {{
          admin
            ? 'Every merchant invoice issued by SwiftDrop.'
            : 'Download the invoice for each billing period.'
        }}
      </p>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      @if (notice()) {
        <p class="success" role="status">{{ notice() }}</p>
      }
      @if (loading()) {
        <p role="status">Loading invoices…</p>
      }
      <div [class.admin-layout]="showGenerator">
        @if (showGenerator) {
          <section class="panel">
            <h2>Create test invoice</h2>
            <p class="notice">
              Development only. Stands in for billing lines that will come from delivered parcels.
              The total is added up by the server.
            </p>
            <form #form="ngForm" (ngSubmit)="generate()">
              <label
                >MERCHANT ID (JWT SUBJECT)<input
                  name="merchantId"
                  required
                  maxlength="255"
                  [(ngModel)]="merchantId"
              /></label>
              <label
                >MERCHANT NAME<input
                  name="merchantName"
                  required
                  maxlength="200"
                  [(ngModel)]="merchantName"
              /></label>
              <div class="form-grid">
                <label
                  >PERIOD START<input
                    name="periodStart"
                    type="date"
                    required
                    [(ngModel)]="periodStart"
                /></label>
                <label
                  >PERIOD END<input
                    name="periodEnd"
                    type="date"
                    required
                    [min]="periodStart"
                    [(ngModel)]="periodEnd"
                /></label>
              </div>
              <p class="eyebrow">BILLING LINES</p>
              @for (line of lines; track line.key; let i = $index) {
                <div class="invoice-line">
                  <label
                    >TRACKING<input
                      [name]="'tracking' + line.key"
                      required
                      maxlength="60"
                      pattern="[A-Za-z0-9_-]+"
                      [(ngModel)]="line.trackingNumber"
                  /></label>
                  <label
                    >AMOUNT (LKR)<input
                      [name]="'amount' + line.key"
                      type="number"
                      required
                      min="0"
                      max="9999999999.99"
                      step="0.01"
                      [(ngModel)]="line.amount"
                  /></label>
                  <label class="wide"
                    >DESCRIPTION<input
                      [name]="'description' + line.key"
                      required
                      maxlength="120"
                      [(ngModel)]="line.description"
                  /></label>
                  <button
                    type="button"
                    class="text-button remove-line"
                    [disabled]="lines.length === 1"
                    (click)="removeLine(i)"
                  >
                    Remove
                  </button>
                </div>
              }
              <button
                type="button"
                class="secondary"
                [disabled]="lines.length >= maxLines"
                (click)="addLine()"
              >
                Add line
              </button>
              <p class="muted">
                {{ lines.length }} {{ lines.length === 1 ? 'parcel' : 'parcels' }} · total
                {{ total() | currency: 'LKR' : 'symbol' : '1.2-2' }}
              </p>
              <button
                class="primary"
                [disabled]="form.invalid || busy() || periodEnd < periodStart"
              >
                {{ busy() ? 'Generating…' : 'Generate invoice' }}
              </button>
            </form>
          </section>
        }
        <div class="invoice-results">
          <section class="panel table-panel">
            <h2>
              {{ admin ? 'All invoices' : 'Your invoices' }}
              <span class="count">{{ invoices().length }}</span>
            </h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>INVOICE / PERIOD</th>
                    <th>PARCELS</th>
                    <th>TOTAL</th>
                    <th>ACTIONS</th>
                  </tr>
                </thead>
                <tbody>
                  @for (invoice of invoices(); track invoice.id) {
                    <tr>
                      <td>
                        <strong class="mono">{{ invoice.invoiceNo }}</strong>
                        <small
                          >{{ invoice.periodStart | date: 'd MMM y' }} →
                          {{ invoice.periodEnd | date: 'd MMM y' }}</small
                        >
                        @if (admin) {
                          <small>{{ invoice.merchantName }}</small>
                        }
                      </td>
                      <td>{{ invoice.lineCount }}</td>
                      <td>
                        {{ invoice.totalAmount | currency: invoice.currency : 'symbol' : '1.2-2' }}
                      </td>
                      <td>
                        <button
                          class="text-button"
                          [disabled]="opening() === invoice.id"
                          (click)="showLines(invoice)"
                        >
                          {{ opening() === invoice.id ? 'Opening…' : 'Lines' }}
                        </button>
                        <button
                          class="text-button"
                          [disabled]="downloading() === invoice.id"
                          (click)="download(invoice)"
                        >
                          {{ downloading() === invoice.id ? 'Downloading…' : 'Download' }}
                        </button>
                      </td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="4">
                        {{
                          loading()
                            ? 'Loading…'
                            : 'No invoices yet. An invoice appears once SwiftDrop bills a period.'
                        }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
          @if (selected(); as detail) {
            <section class="panel table-panel" aria-label="Invoice lines">
              <h2>
                <span class="mono">{{ detail.invoice.invoiceNo }}</span> lines
                <span class="count">{{ detail.lines.length }}</span>
              </h2>
              <div class="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>NO</th>
                      <th>TRACKING</th>
                      <th>DESCRIPTION</th>
                      <th>AMOUNT</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (line of detail.lines; track line.lineNo) {
                      <tr>
                        <td>{{ line.lineNo }}</td>
                        <td class="mono">{{ line.trackingNumber }}</td>
                        <td>{{ line.description }}</td>
                        <td>
                          {{ line.amount | currency: detail.invoice.currency : 'symbol' : '1.2-2' }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <p class="muted lines-total">
                Total
                {{
                  detail.invoice.totalAmount
                    | currency: detail.invoice.currency : 'symbol' : '1.2-2'
                }}
                <button class="text-button" (click)="selected.set(null)">Close</button>
              </p>
            </section>
          }
        </div>
      </div>
    </main>
  `,
})
export class InvoicesPage implements OnInit {
  private readonly api = inject(PricingApi);
  private readonly path = '/api/v1/invoices';
  readonly admin = this.api.role() === 'ADMIN';
  readonly showGenerator = this.admin && environment.development;
  readonly maxLines = MAX_FORM_LINES;
  readonly invoices = signal<Invoice[]>([]);
  readonly selected = signal<InvoiceDetail | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly opening = signal('');
  readonly downloading = signal('');
  readonly error = signal('');
  readonly notice = signal('');

  merchantId = 'pricing-demo-merchant';
  merchantName = 'Ceylon Spice Traders';
  periodStart = '';
  periodEnd = '';
  lines: DraftLine[] = [];
  private nextKey = 0;

  constructor() {
    this.resetForm();
  }

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
    this.invoices.set(await this.api.all<Invoice>(this.path));
  }

  /** The total shown here is only a preview; the server adds the lines up itself. */
  total(): number {
    const cents = this.lines.reduce(
      (sum, line) => sum + Math.round((Number(line.amount) || 0) * 100),
      0,
    );
    return cents / 100;
  }

  addLine() {
    this.lines.push(this.newLine('', '', null));
  }

  removeLine(index: number) {
    this.lines.splice(index, 1);
  }

  async generate() {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    try {
      const body: InvoiceInput = {
        merchantId: this.merchantId.trim(),
        merchantName: this.merchantName.trim(),
        periodStart: this.periodStart,
        periodEnd: this.periodEnd,
        lines: this.lines.map((line) => ({
          parcelId: line.parcelId,
          trackingNumber: line.trackingNumber.trim(),
          description: line.description.trim(),
          amount: Number(line.amount),
        })),
      };
      const invoice = await this.api.save<Invoice>(`${this.path}/generate`, body);
      this.notice.set(`Invoice ${invoice.invoiceNo} generated for ${invoice.merchantName}.`);
      this.resetForm();
      await this.reload();
    } catch (error) {
      this.error.set(errorMessage(error, DOCUMENT_HINT));
    } finally {
      this.busy.set(false);
    }
  }

  async showLines(invoice: Invoice) {
    this.opening.set(invoice.id);
    this.error.set('');
    try {
      this.selected.set(await this.api.getInvoice(invoice.id));
    } catch (error) {
      this.error.set(errorMessage(error, DOCUMENT_HINT));
    } finally {
      this.opening.set('');
    }
  }

  /** Turns the authenticated PDF bytes into a temporary local file the browser can save. */
  async download(invoice: Invoice) {
    this.downloading.set(invoice.id);
    this.error.set('');
    try {
      const pdf = await this.api.downloadInvoice(invoice.id);
      const url = URL.createObjectURL(pdf);
      const link = document.createElement('a');
      link.href = url;
      link.download = `invoice-${invoice.invoiceNo}.pdf`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      this.error.set(errorMessage(error, DOCUMENT_HINT));
    } finally {
      this.downloading.set('');
    }
  }

  /** Starts a fresh form for last month, with two sample lines. Each line gets its own parcel id. */
  private resetForm() {
    const now = new Date();
    this.periodStart = this.isoDay(new Date(now.getFullYear(), now.getMonth() - 1, 1));
    this.periodEnd = this.isoDay(new Date(now.getFullYear(), now.getMonth(), 0));
    this.lines = [
      this.newLine('SD-1001-0001', 'Colombo to Kandy, Standard', 550),
      this.newLine('SD-1001-0002', 'Colombo to Kandy, Express', 687.5),
    ];
  }

  private newLine(trackingNumber: string, description: string, amount: number | null): DraftLine {
    return {
      key: this.nextKey++,
      parcelId: crypto.randomUUID(),
      trackingNumber,
      description,
      amount,
    };
  }

  private isoDay(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }
}
