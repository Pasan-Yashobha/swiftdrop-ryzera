import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatusChange } from '../../../core/models/order.model';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { statusPresentation } from '../../../shared/ui/status-badge/status-badge';

/**
 * Status history as an ordered list, oldest first, so the list reads as the parcel's journey and the
 * current state comes last. The current state is marked in text as well as by style.
 */
@Component({
  selector: 'app-order-timeline',
  imports: [DatePipe, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-timeline.html',
  styleUrl: './order-timeline.css',
})
export class OrderTimeline {
  readonly history = input.required<readonly OrderStatusChange[]>();

  protected readonly steps = computed(() => {
    const ordered = [...this.history()].sort(
      (a, b) => Date.parse(a.occurredAt) - Date.parse(b.occurredAt),
    );
    return ordered.map((change, index) => ({
      ...change,
      label: statusPresentation(change.toStatus).label,
      current: index === ordered.length - 1,
    }));
  });
}
