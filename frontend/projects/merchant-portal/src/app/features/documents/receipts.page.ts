import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DocumentApi } from '../../core/api/document.api';
import { AppError, toAppError } from '../../core/http/app-error';
import { Receipt } from '../../core/models/document.model';
import { PageInfo } from '../../core/models/page.model';
import { ItemPage } from '../../core/models/pricing.model';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { downloadBlob } from '../../shared/util/download-file';

export const RECEIPTS_PAGE_SIZE = 20;

/**
 * The signed-in customer's delivery receipts (Document service). The server returns only the
 * customer's own receipts; Admin sees every receipt in the admin console.
 */
@Component({
  selector: 'app-receipts-page',
  imports: [DatePipe, MoneyPipe, EmptyState, ErrorAlert, Pagination, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './receipts.page.html',
  styleUrls: ['../route-board.css', './documents.css'],
})
export class ReceiptsPage {
  private readonly api = inject(DocumentApi);
  private readonly toast = inject(ToastService);

  protected readonly page = signal<ItemPage<Receipt> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
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
    this.api.receipts(pageNumber, RECEIPTS_PAGE_SIZE).subscribe({
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

  protected download(receipt: Receipt): void {
    this.downloading.set(receipt.id);
    this.api.receiptPdf(receipt.id).subscribe({
      next: (pdf) => {
        downloadBlob(pdf, `receipt-${receipt.receiptNo}.pdf`);
        this.downloading.set(null);
      },
      error: (error: unknown) => {
        this.toast.error(toAppError(error).message);
        this.downloading.set(null);
      },
    });
  }
}
