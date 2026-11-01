import { TestBed } from '@angular/core/testing';
import { DailyOrderCount } from '../../../core/models/order.model';
import { element } from '../../../core/testing/dom';
import { OrdersChart } from './orders-chart';

const WEEK: DailyOrderCount[] = [
  { date: '2026-10-26', count: 0 },
  { date: '2026-10-27', count: 4 },
  { date: '2026-10-28', count: 9 },
  { date: '2026-10-29', count: 1 },
  { date: '2026-10-30', count: 0 },
  { date: '2026-10-31', count: 2 },
  { date: '2026-11-01', count: 3 },
];

function render(days: DailyOrderCount[]) {
  const fixture = TestBed.createComponent(OrdersChart);
  fixture.componentRef.setInput('days', days);
  fixture.detectChanges();
  return fixture;
}

describe('OrdersChart', () => {
  it('draws one bar per day with heights in proportion to the counts', () => {
    const bars = Array.from(element(render(WEEK)).querySelectorAll('rect.chart__bar'));
    const heights = bars.map((bar) => Number(bar.getAttribute('height')));

    expect(bars.length).toBe(7);
    expect(heights[0]).toBe(0);
    expect(heights[2]).toBeGreaterThan(heights[1]);
    expect(heights[1] / heights[3]).toBeCloseTo(4, 5);
    expect(heights[2] / heights[3]).toBeCloseTo(9, 5);
  });

  it('keeps every bar inside the plotting area', () => {
    const root = element(render(WEEK));
    const viewBox =
      root.querySelector('svg')?.getAttribute('viewBox')?.split(' ').map(Number) ?? [];

    for (const bar of Array.from(root.querySelectorAll('rect.chart__bar'))) {
      const top = Number(bar.getAttribute('y'));
      const bottom = top + Number(bar.getAttribute('height'));
      expect(top).toBeGreaterThanOrEqual(0);
      expect(bottom).toBeLessThanOrEqual(viewBox[3]);
    }
  });

  it('labels the bars with their counts and the weekday', () => {
    const labels = Array.from(element(render(WEEK)).querySelectorAll('text')).map((text) =>
      text.textContent?.trim(),
    );

    expect(labels).toContain('9');
    expect(labels).toEqual(
      jasmine.arrayContaining(['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']),
    );
  });

  it('is a single labelled image with a text summary', () => {
    const svg = element(render(WEEK)).querySelector('svg') as SVGElement;
    const [titleId, descriptionId] = (svg.getAttribute('aria-labelledby') ?? '').split(' ');

    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.querySelector(`#${titleId}`)?.textContent).toBe(
      'Orders created per day, last 7 days',
    );
    expect(svg.querySelector(`#${descriptionId}`)?.textContent).toBe(
      '19 orders in the last 7 days. The busiest day was Wednesday, October 28 with 9. The data is also available as a table.',
    );
  });

  it('gives each bar a tooltip and offers the same data as a table', () => {
    const root = element(render(WEEK));
    const rows = Array.from(root.querySelectorAll('table tbody tr')).map((row) =>
      Array.from(row.children).map((cell) => cell.textContent?.trim()),
    );

    expect(root.querySelector('rect.chart__bar title')?.textContent).toBe('Monday, October 26: 0');
    expect(root.querySelector('table caption')?.textContent).toContain('Orders created per day');
    expect(rows.length).toBe(7);
    expect(rows[2]).toEqual(['Wednesday, October 28', '9']);
    expect(root.querySelector('table')?.classList).toContain('visually-hidden');
  });

  it('says so when there were no orders', () => {
    const root = element(render(WEEK.map((day) => ({ ...day, count: 0 }))));

    expect(root.querySelector('figcaption')?.textContent).toContain('No orders were created');
    expect(root.querySelector('desc')?.textContent).toBe(
      'No orders were created in the last 7 days.',
    );
  });

  it('uses singular wording for a single order and never shows a time zone shifted day', () => {
    const root = element(render([{ date: '2026-11-01', count: 1 }]));

    expect(root.querySelector('desc')?.textContent).toContain('1 order in the last 7 days');
    expect(root.querySelector('table tbody th')?.textContent).toBe('Sunday, November 1');
  });
});
