import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DailyOrderCount } from '../../../core/models/order.model';

const WIDTH = 420;
const HEIGHT = 220;
const MARGIN = { top: 24, right: 8, bottom: 32, left: 36 };
const GRID_LINES = 4;
const MAX_BAR_WIDTH = 40;

// Stats dates are calendar days; format them in UTC so the viewer's time zone cannot shift them.
const SHORT_DAY = new Intl.DateTimeFormat('en', { weekday: 'short', timeZone: 'UTC' });
const LONG_DAY = new Intl.DateTimeFormat('en', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  timeZone: 'UTC',
});

let nextChartId = 0;

/**
 * Bar chart of orders per day in plain SVG. The drawing is one image with a text summary; the same
 * numbers are available to assistive technology as a (visually hidden) data table.
 */
@Component({
  selector: 'app-orders-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './orders-chart.html',
  styleUrl: './orders-chart.css',
})
export class OrdersChart {
  /** One entry per day, oldest first. */
  readonly days = input.required<readonly DailyOrderCount[]>();

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly left = MARGIN.left;
  protected readonly right = WIDTH - MARGIN.right;
  protected readonly baseline = HEIGHT - MARGIN.bottom;
  protected readonly titleId = `orders-chart-title-${nextChartId}`;
  protected readonly descriptionId = `orders-chart-desc-${nextChartId++}`;

  protected readonly total = computed(() => this.days().reduce((sum, day) => sum + day.count, 0));

  protected readonly summary = computed(() => {
    const total = this.total();
    if (total === 0) {
      return 'No orders were created in the last 7 days.';
    }
    const busiest = this.days().reduce((best, day) => (day.count > best.count ? day : best));
    return `${total} ${total === 1 ? 'order' : 'orders'} in the last 7 days. The busiest day was ${describeDay(busiest.date)} with ${busiest.count}. The data is also available as a table.`;
  });

  private readonly scale = computed(() => {
    const peak = Math.max(1, ...this.days().map((day) => day.count));
    const step = Math.ceil(peak / GRID_LINES);
    return { step, top: step * GRID_LINES, height: this.baseline - MARGIN.top };
  });

  protected readonly gridLines = computed(() => {
    const { step, top, height } = this.scale();
    return Array.from({ length: GRID_LINES + 1 }, (_, index) => ({
      value: index * step,
      y: this.baseline - ((index * step) / top) * height,
    }));
  });

  protected readonly bars = computed(() => {
    const days = this.days();
    const { top, height } = this.scale();
    const band = (this.right - this.left) / days.length;
    const barWidth = Math.min(band * 0.6, MAX_BAR_WIDTH);
    return days.map((day, index) => {
      const barHeight = (day.count / top) * height;
      const x = this.left + index * band + (band - barWidth) / 2;
      return {
        date: day.date,
        count: day.count,
        x,
        y: this.baseline - barHeight,
        width: barWidth,
        height: barHeight,
        centre: x + barWidth / 2,
        label: SHORT_DAY.format(utcDate(day.date)),
        description: describeDay(day.date),
      };
    });
  });
}

function utcDate(isoDay: string): Date {
  return new Date(`${isoDay}T00:00:00Z`);
}

function describeDay(isoDay: string): string {
  return LONG_DAY.format(utcDate(isoDay));
}
