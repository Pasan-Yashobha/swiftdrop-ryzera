import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StopState, TrackingStop } from '../../../core/models/tracking.model';

const STATE_TEXT: Readonly<Record<StopState, string>> = {
  DONE: 'done',
  CURRENT: 'current stop',
  ATTENTION: 'needs attention',
  EXCEPTION: 'problem',
  PENDING: 'not yet',
};

/**
 * Route Board "transit line": the parcel status as a line of numbered stops (01 Created ... 05 Delivered).
 * Shared by the public tracking page and the customer app. `variant="board"` for black panels, `"light"` for cards.
 */
@Component({
  selector: 'app-transit-line',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './transit-line.html',
  styleUrl: './transit-line.css',
})
export class TransitLine {
  readonly stops = input.required<TrackingStop[]>();
  readonly variant = input<'board' | 'light'>('board');

  protected number(stop: number): string {
    return String(stop).padStart(2, '0');
  }

  protected stateText(state: StopState): string {
    return STATE_TEXT[state];
  }
}
