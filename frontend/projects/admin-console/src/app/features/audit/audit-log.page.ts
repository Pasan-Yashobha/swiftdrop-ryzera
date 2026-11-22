import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuditAdminApi } from '../../core/api/audit-admin.api';
import { AppError, toAppError } from '../../core/http/app-error';
import {
  AUDIT_ENTITY_TYPES,
  AuditFilters,
  AuditLog,
  ChainVerification,
} from '../../core/models/audit.model';
import { PageResponse } from '../../core/models/page.model';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorAlert } from '../../shared/ui/error-alert/error-alert';
import { Pagination } from '../../shared/ui/pagination/pagination';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { dateRangeValidator, endOfDayUtc, startOfDayUtc } from '../../shared/util/date-range';
import { AuditDetailDialog } from './audit-detail-dialog';

export const AUDIT_PAGE_SIZE = 20;

/** /admin/audit: who did what and when, with filters, record details and a hash-chain integrity check. */
@Component({
  selector: 'app-audit-log-page',
  imports: [ReactiveFormsModule, DatePipe, Pagination, ErrorAlert, EmptyState, Spinner, AuditDetailDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-log.page.html',
  styleUrl: './audit-log.page.css',
})
export class AuditLogPage {
  protected readonly entityTypes = AUDIT_ENTITY_TYPES;

  protected readonly filters = new FormGroup(
    {
      action: new FormControl('', { nonNullable: true }),
      actor: new FormControl('', { nonNullable: true }),
      entityType: new FormControl('', { nonNullable: true }),
      entityId: new FormControl('', { nonNullable: true }),
      from: new FormControl('', { nonNullable: true }),
      to: new FormControl('', { nonNullable: true }),
    },
    { validators: dateRangeValidator() },
  );

  protected readonly result = signal<PageResponse<AuditLog> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<AppError | null>(null);

  protected readonly verification = signal<ChainVerification | null>(null);
  protected readonly verifying = signal(false);
  protected readonly verifyError = signal<AppError | null>(null);

  private readonly detail = viewChild.required(AuditDetailDialog);
  private readonly api = inject(AuditAdminApi);
  private applied: AuditFilters = {};
  private pageIndex = 0;
  private request: Subscription | null = null;
  private verifyRequest: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.request?.unsubscribe();
      this.verifyRequest?.unsubscribe();
    });
    this.load();
  }

  protected applyFilters(): void {
    if (this.filters.invalid) {
      this.filters.markAllAsTouched();
      return;
    }
    const value = this.filters.getRawValue();
    this.applied = {
      action: value.action.trim() || null,
      actor: value.actor.trim() || null,
      entityType: value.entityType || null,
      entityId: value.entityId.trim() || null,
      from: startOfDayUtc(value.from),
      to: endOfDayUtc(value.to),
    };
    this.pageIndex = 0;
    this.load();
  }

  protected resetFilters(): void {
    this.filters.reset();
    this.applied = {};
    this.pageIndex = 0;
    this.load();
  }

  protected goToPage(page: number): void {
    this.pageIndex = page;
    this.load();
  }

  protected load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);
    this.request = this.api
      .list({ ...this.applied, page: this.pageIndex, size: AUDIT_PAGE_SIZE })
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.error.set(toAppError(error));
          this.loading.set(false);
        },
      });
  }

  /** Asks the server to re-compute the whole hash chain and shows where it breaks, if it does. */
  protected verifyChain(): void {
    this.verifyRequest?.unsubscribe();
    this.verifying.set(true);
    this.verifyError.set(null);
    this.verification.set(null);
    this.verifyRequest = this.api.verify().subscribe({
      next: (result) => {
        this.verification.set(result);
        this.verifying.set(false);
      },
      error: (error: unknown) => {
        this.verifyError.set(toAppError(error));
        this.verifying.set(false);
      },
    });
  }

  protected openDetail(entry: AuditLog, trigger: HTMLElement | null = null): void {
    this.detail().open(entry, trigger);
  }

  /** Mouse convenience: a click anywhere on a row opens it, except on the row's own buttons. */
  protected onRowClick(event: MouseEvent, entry: AuditLog): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest('button, a')) {
      return;
    }
    this.openDetail(entry);
  }
}
