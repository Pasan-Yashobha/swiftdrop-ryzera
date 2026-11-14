import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { PageInfo } from '../../../core/models/page.model';

/** Previous / next pager with a "Showing x-y of n" summary. Pass the whole page response as `info`. */
@Component({
  selector: 'app-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (info().totalElements > 0) {
      <nav class="pagination" aria-label="Pagination">
        <p class="muted small">
          Showing {{ firstItem() }}-{{ lastItem() }} of {{ info().totalElements }}
        </p>
        <div class="cluster">
          <button
            type="button"
            class="btn btn-ghost btn-sm"
            [disabled]="info().page <= 0"
            (click)="go(info().page - 1)"
          >
            Previous
          </button>
          <span class="small" aria-current="page"
            >Page {{ info().page + 1 }} of {{ info().totalPages }}</span
          >
          <button
            type="button"
            class="btn btn-ghost btn-sm"
            [disabled]="info().page + 1 >= info().totalPages"
            (click)="go(info().page + 1)"
          >
            Next
          </button>
        </div>
      </nav>
    }
  `,
  styles: `
    .pagination {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
    }
  `,
})
export class Pagination {
  readonly info = input.required<PageInfo>();
  /** Emits the zero-based page the user asked for. */
  readonly pageChange = output<number>();

  protected readonly firstItem = computed(() => this.info().page * this.info().size + 1);
  protected readonly lastItem = computed(() =>
    Math.min((this.info().page + 1) * this.info().size, this.info().totalElements),
  );

  protected go(page: number): void {
    this.pageChange.emit(page);
  }
}
