import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../../../core/api/order.api';
import { AppError, toAppError } from '../../../core/http/app-error';
import { BulkResult } from '../../../core/models/order.model';
import { ErrorAlert } from '../../../shared/ui/error-alert/error-alert';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { BulkResultTable } from '../components/bulk-result-table';
import { bulkFileProblem, formatBytes, MAX_BULK_ROWS } from '../util/bulk-file';
import { failedRowsCsv } from '../util/bulk-report';
import { downloadBlob } from '../util/download-file';

const TEMPLATE_FILE_NAME = 'swiftdrop-bulk-orders-template.csv';
const FAILED_ROWS_FILE_NAME = 'swiftdrop-bulk-failed-rows.csv';

/** Create many orders from a CSV file and review, row by row, what happened. */
@Component({
  selector: 'app-order-bulk-page',
  imports: [RouterLink, BulkResultTable, ErrorAlert, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-bulk.page.html',
  styleUrl: './order-bulk.page.css',
})
export class OrderBulkPage {
  protected readonly maxRows = MAX_BULK_ROWS;

  protected readonly file = signal<File | null>(null);
  protected readonly fileProblem = signal<string | null>(null);
  protected readonly dragging = signal(false);
  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<AppError | null>(null);
  protected readonly result = signal<BulkResult | null>(null);
  protected readonly templateBusy = signal(false);
  protected readonly templateError = signal<AppError | null>(null);

  protected readonly fileSize = computed(() => formatBytes(this.file()?.size ?? 0));
  /** What to try after the server rejected the whole file (400 or 413). */
  protected readonly uploadHint = computed(() => {
    switch (this.uploadError()?.status) {
      case 413:
        return `A file can have up to ${MAX_BULK_ROWS} rows and be 1 MiB in size. Split it into smaller files.`;
      case 400:
        return 'Download the template and check that your column names and their order match it.';
      default:
        return null;
    }
  });

  private readonly api = inject(OrderApi);
  private readonly destroyRef = inject(DestroyRef);

  protected downloadTemplate(): void {
    this.templateBusy.set(true);
    this.templateError.set(null);
    this.api
      .downloadBulkTemplate()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (template) => {
          this.templateBusy.set(false);
          downloadBlob(template, TEMPLATE_FILE_NAME);
        },
        error: (error: unknown) => {
          this.templateBusy.set(false);
          this.templateError.set(toAppError(error));
        },
      });
  }

  protected onFilePicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.choose(input.files?.item(0) ?? null);
    // Allows choosing the same file again after fixing it.
    input.value = '';
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDragLeave(): void {
    this.dragging.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (!this.uploading()) {
      this.choose(event.dataTransfer?.files.item(0) ?? null);
    }
  }

  protected removeFile(): void {
    this.file.set(null);
    this.fileProblem.set(null);
    this.uploadError.set(null);
  }

  protected upload(): void {
    const file = this.file();
    if (!file || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.uploadError.set(null);
    this.api
      .uploadBulk(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.uploading.set(false);
          this.result.set(result);
        },
        error: (error: unknown) => {
          this.uploading.set(false);
          this.uploadError.set(toAppError(error));
        },
      });
  }

  protected downloadFailedRows(): void {
    const results = this.result()?.results ?? [];
    downloadBlob(
      new Blob([failedRowsCsv(results)], { type: 'text/csv;charset=utf-8' }),
      FAILED_ROWS_FILE_NAME,
    );
  }

  protected uploadAnother(): void {
    this.result.set(null);
    this.removeFile();
  }

  private choose(file: File | null): void {
    if (!file) {
      return;
    }
    const problem = bulkFileProblem(file);
    this.fileProblem.set(problem);
    this.file.set(problem ? null : file);
    this.uploadError.set(null);
  }
}
