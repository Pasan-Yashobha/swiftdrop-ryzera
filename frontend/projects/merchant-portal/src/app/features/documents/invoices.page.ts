import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DocumentApi } from '../../core/api/document.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { Invoice, InvoiceDetail } from '../../core/models/document.model';
import { PageInfo } from '../../core/models/page.model';
import { ItemPage } from '../../core/models/pricing.model';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { downloadBlob } from '../../shared/util/download-file';

export const INVOICES_PAGE_SIZE = 20;

/**
 * The signed-in merchant's invoices (Document service), with the parcels billed on each. The server
 * returns only the merchant's own invoices; Admin sees every invoice in the admin console.
 */
@Component({
  selector: 'app-invoices-page',
  imports: [DatePipe, MoneyPipe, EmptyState, ErrorAlert, Pagination, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invoices.page.html',
  styleUrls: ['../route-board.css', './documents.css'],
})
export class InvoicesPage {
  private readonly api = inject(DocumentApi);
  private readonly toast = inject(ToastService);

  protected readonly page = signal<ItemPage<Invoice> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly selected = signal<InvoiceDetail | null>(null);
  protected readonly opening = signal<string | null>(null);
  protected readonly downloading = signal<string | null>(null);

  protected readonly pageInfo = computed<PageInfo | null>(() => {
    const page = this.page();
    return page
      ? {
          page: page.page,
          size: page.size,
          totalElements: page.totalElements,
          totalPages: Math.max(1, Math.ceil(page.totalElements / page.size)),
        }
      : null;
  });

  constructor() {
    this.load(0);
  }

  protected load(pageNumber: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.invoices(pageNumber, INVOICES_PAGE_SIZE).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(toAppError(error));
        this.loading.set(false);
      },
    });
  }

  protected toggleLines(invoice: Invoice): void {
    if (this.selected()?.invoice.id === invoice.id) {
      this.selected.set(null);
      return;
    }
    this.opening.set(invoice.id);
    this.api.invoice(invoice.id).subscribe({
      next: (detail) => {
        this.selected.set(detail);
        this.opening.set(null);
      },
      error: (error: unknown) => {
        this.toast.error(toAppError(error).message);
        this.opening.set(null);
      },
    });
  }

  protected download(invoice: Invoice): void {
    this.downloading.set(invoice.id);
    this.api.invoicePdf(invoice.id).subscribe({
      next: (pdf) => {
        downloadBlob(pdf, `invoice-${invoice.invoiceNo}.pdf`);
        this.downloading.set(null);
      },
      error: (error: unknown) => {
        this.toast.error(toAppError(error).message);
        this.downloading.set(null);
      },
    });
  }
}
