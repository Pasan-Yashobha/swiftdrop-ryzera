import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DocumentApi } from '../../core/api/document.api';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { AppError, toAppError } from '../../core/http/app-error';
import { Receipt } from '../../core/models/document.model';
import { PageInfo } from '../../core/models/page.model';
import { ItemPage } from '../../core/models/pricing.model';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../shared/ui/form-field/form-field';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { downloadBlob } from '../../shared/util/download-file';
import { generateId } from '../../shared/util/id';

export const RECEIPTS_PAGE_SIZE = 20;

type AmountName = 'baseAmount' | 'weightAmount' | 'sizeAmount' | 'expressAmount' | 'discountAmount';

/** The server accepts up to 14 integer digits; a whole-number limit keeps the value exact in JavaScript. */
const MAX_AMOUNT = 99_999_999_999_999;

/**
 * Delivery receipts (Document service). Customers see their own; Admin sees all and, in development
 * builds only, can create a test receipt in place of the parcel.status.delivered event.
 */
@Component({
  selector: 'app-receipts-page',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MoneyPipe,
    EmptyState,
    ErrorAlert,
    FieldControl,
    FormField,
    Pagination,
    Spinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './receipts.page.html',
  styleUrl: './documents.css',
})
export class ReceiptsPage {
  private readonly api = inject(DocumentApi);
  private readonly toast = inject(ToastService);
  protected readonly isAdmin = inject(AuthService).role() === 'ADMIN';
  protected readonly showTestForm = this.isAdmin && !inject(APP_CONFIG).production;

  protected readonly page = signal<ItemPage<Receipt> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly downloading = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly createError = signal<AppError | null>(null);

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

  protected readonly amountFields: { name: AmountName; label: string }[] = [
    { name: 'baseAmount', label: 'Base (LKR)' },
    { name: 'weightAmount', label: 'Weight (LKR)' },
    { name: 'sizeAmount', label: 'Size (LKR)' },
    { name: 'expressAmount', label: 'Express (LKR)' },
    { name: 'discountAmount', label: 'Discount (LKR)' },
  ];

  protected readonly testForm = new FormGroup({
    customerId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255)],
    }),
    trackingNumber: new FormControl(sampleTrackingNumber(), {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(60), Validators.pattern(/^[A-Za-z0-9_-]+$/)],
    }),
    baseAmount: amountControl(300),
    weightAmount: amountControl(200),
    sizeAmount: amountControl(50),
    expressAmount: amountControl(0),
    discountAmount: amountControl(0),
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

  /** The total is derived from the charges, so it always passes the server's consistency check. */
  protected total(): number {
    const value = this.testForm.getRawValue();
    const cents = (name: AmountName) => Math.round(Number(value[name] ?? 0) * 100);
    return (
      (cents('baseAmount') +
        cents('weightAmount') +
        cents('sizeAmount') +
        cents('expressAmount') -
        cents('discountAmount')) /
      100
    );
  }

  protected createTestReceipt(): void {
    if (this.testForm.invalid || this.total() < 0) {
      this.testForm.markAllAsTouched();
      return;
    }
    const value = this.testForm.getRawValue();
    this.creating.set(true);
    this.createError.set(null);
    this.api
      .createTestReceipt({
        parcelId: generateId(),
        customerId: value.customerId.trim(),
        trackingNumber: value.trackingNumber,
        deliveredAt: new Date(Date.now() - 60_000).toISOString(),
        baseAmount: Number(value.baseAmount),
        weightAmount: Number(value.weightAmount),
        sizeAmount: Number(value.sizeAmount),
        expressAmount: Number(value.expressAmount),
        discountAmount: Number(value.discountAmount),
        totalAmount: this.total(),
      })
      .subscribe({
        next: (receipt) => {
          this.creating.set(false);
          this.toast.success(`Receipt ${receipt.receiptNo} created.`);
          this.testForm.controls.trackingNumber.setValue(sampleTrackingNumber());
          this.load(0);
        },
        error: (error: unknown) => {
          this.creating.set(false);
          this.createError.set(toAppError(error));
        },
      });
  }
}

function amountControl(initial: number): FormControl<number | null> {
  return new FormControl<number | null>(initial, {
    validators: [Validators.required, Validators.min(0), Validators.max(MAX_AMOUNT)],
  });
}

function sampleTrackingNumber(): string {
  const part = () => Math.floor(1000 + Math.random() * 9000);
  return `SD-${part()}-${part()}`;
}
