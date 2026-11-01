import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BulkRowResult } from '../../../core/models/order.model';
import { StatusBadge } from '../../../shared/ui/status-badge/status-badge';

/** One line per file row: created (with a link to the order) or failed (with the reasons). */
@Component({
  selector: 'app-bulk-result-table',
  imports: [RouterLink, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './bulk-result-table.html',
  styleUrl: './bulk-result-table.css',
})
export class BulkResultTable {
  readonly results = input.required<readonly BulkRowResult[]>();

  protected readonly failedOnly = signal(false);
  protected readonly failedCount = computed(
    () => this.results().filter((result) => result.status === 'FAILED').length,
  );
  protected readonly visibleRows = computed(() =>
    this.failedOnly()
      ? this.results().filter((result) => result.status === 'FAILED')
      : this.results(),
  );

  protected setFailedOnly(event: Event): void {
    this.failedOnly.set((event.target as HTMLInputElement).checked);
  }
}
