import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { DocumentApi } from '../../core/api/document.api';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { AppError, toAppError } from '../../core/http/app-error';
import { Invoice, InvoiceDetail } from '../../core/models/document.model';
import { PageInfo } from '../../core/models/page.model';
import { ItemPage } from '../../core/models/pricing.model';
import { notBlank } from '../../shared/forms/validators';
import { MoneyPipe } from '../../shared/pipes/money.pipe';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { FieldControl, FormField } from '../../shared/ui/form-field/form-field';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { downloadBlob } from '../../shared/util/download-file';
import { generateId } from '../../shared/util/id';

export const INVOICES_PAGE_SIZE = 20;
/** The Document service accepts up to 500 lines; the test form keeps it smaller. */
export const MAX_TEST_LINES = 50;
const MAX_LINE_AMOUNT = 9_999_999_999.99;

type LineGroup = FormGroup<{
  parcelId: FormControl<string>;
  trackingNumber: FormControl<string>;
  description: FormControl<string>;
  amount: FormControl<number | null>;
}>;

/** The billing period must not end before it starts. */
function periodInOrder(group: AbstractControl): ValidationErrors | null {
  const start = group.get('periodStart')?.value as string;
  const end = group.get('periodEnd')?.value as string;
  return start && end && end < start ? { periodOrder: true } : null;
}

/**
 * Merchant invoices (Document service). Merchants see their own; Admin sees all and, in development
 * builds only, can create a test invoice from billing lines. The server adds up the total.
 */
@Component({
  selector: 'app-invoices-page',
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
  templateUrl: './invoices.page.html',
  styleUrls: ['../route-board.css', './documents.css'],
})
export class InvoicesPage {
  private readonly api = inject(DocumentApi);
  private readonly toast = inject(ToastService);
  protected readonly isAdmin = inject(AuthService).role() === 'ADMIN';
  protected readonly showTestForm = this.isAdmin && !inject(APP_CONFIG).production;
  protected readonly maxLines = MAX_TEST_LINES;

  protected readonly page = signal<ItemPage<Invoice> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly selected = signal<InvoiceDetail | null>(null);
  protected readonly opening = signal<string | null>(null);
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

  protected readonly testForm = new FormGroup(
    {
      merchantId: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, notBlank, Validators.maxLength(255)],
      }),
      merchantName: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, notBlank, Validators.maxLength(200)],
      }),
      periodStart: new FormControl(firstOfLastMonth(), {
        nonNullable: true,
        validators: Validators.required,
      }),
      periodEnd: new FormControl(lastOfLastMonth(), {
        nonNullable: true,
        validators: Validators.required,
      }),
      lines: new FormArray<LineGroup>([]),
    },
    { validators: periodInOrder },
  );

  constructor() {
    this.resetTestForm();
    this.load(0);
  }

  protected get lines(): FormArray<LineGroup> {
    return this.testForm.controls.lines;
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

  /** Only a preview: the server adds the lines up itself. */
  protected total(): number {
    const cents = this.lines.controls.reduce(
      (sum, line) => sum + Math.round(Number(line.controls.amount.value ?? 0) * 100),
      0,
    );
    return cents / 100;
  }

  protected addLine(trackingNumber = '', description = '', amount: number | null = null): void {
    if (this.lines.length >= MAX_TEST_LINES) {
      return;
    }
    this.lines.push(
      new FormGroup({
        // Each billed parcel needs its own id; a parcel can be billed on one invoice only.
        parcelId: new FormControl(generateId(), { nonNullable: true }),
        trackingNumber: new FormControl(trackingNumber, {
          nonNullable: true,
          validators: [
            Validators.required,
            Validators.maxLength(60),
            Validators.pattern(/^[A-Za-z0-9_-]+$/),
          ],
        }),
        description: new FormControl(description, {
          nonNullable: true,
          validators: [Validators.required, notBlank, Validators.maxLength(120)],
        }),
        amount: new FormControl<number | null>(amount, {
          validators: [Validators.required, Validators.min(0), Validators.max(MAX_LINE_AMOUNT)],
        }),
      }),
    );
  }

  protected removeLine(index: number): void {
    this.lines.removeAt(index);
  }

  protected createTestInvoice(): void {
    if (this.testForm.invalid || this.lines.length === 0) {
      this.testForm.markAllAsTouched();
      return;
    }
    const value = this.testForm.getRawValue();
    this.creating.set(true);
    this.createError.set(null);
    this.api
      .generateInvoice({
        merchantId: value.merchantId.trim(),
        merchantName: value.merchantName.trim(),
        periodStart: value.periodStart,
        periodEnd: value.periodEnd,
        lines: value.lines.map((line) => ({
          parcelId: line.parcelId,
          trackingNumber: line.trackingNumber.trim(),
          description: line.description.trim(),
          amount: Number(line.amount),
        })),
      })
      .subscribe({
        next: (invoice) => {
          this.creating.set(false);
          this.toast.success(`Invoice ${invoice.invoiceNo} created for ${invoice.merchantName}.`);
          this.resetTestForm();
          this.load(0);
        },
        error: (error: unknown) => {
          this.creating.set(false);
          this.createError.set(toAppError(error));
        },
      });
  }

  private resetTestForm(): void {
    this.lines.clear();
    this.testForm.reset({ periodStart: firstOfLastMonth(), periodEnd: lastOfLastMonth() });
    this.addLine(sampleTracking(), 'Colombo to Kandy, Standard', 550);
    this.addLine(sampleTracking(), 'Colombo to Kandy, Express', 687.5);
  }
}

function isoDay(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function firstOfLastMonth(): string {
  const now = new Date();
  return isoDay(new Date(now.getFullYear(), now.getMonth() - 1, 1));
}

function lastOfLastMonth(): string {
  const now = new Date();
  return isoDay(new Date(now.getFullYear(), now.getMonth(), 0));
}

function sampleTracking(): string {
  const part = () => Math.floor(1000 + Math.random() * 9000);
  return `SD-${part()}-${part()}`;
}
