import { TestBed } from '@angular/core/testing';
import { OrderStatusChange } from '../../../core/models/order.model';
import { element } from '../../../core/testing/dom';
import { OrderTimeline } from './order-timeline';

function render(history: OrderStatusChange[]) {
  const fixture = TestBed.createComponent(OrderTimeline);
  fixture.componentRef.setInput('history', history);
  fixture.detectChanges();
  return fixture;
}

const HISTORY: OrderStatusChange[] = [
  {
    fromStatus: 'IN_TRANSIT',
    toStatus: 'FAILED',
    occurredAt: '2026-10-03T10:00:00Z',
    note: 'Recipient unavailable',
  },
  { fromStatus: null, toStatus: 'CREATED', occurredAt: '2026-10-01T08:00:00Z', note: null },
  {
    fromStatus: 'CREATED',
    toStatus: 'IN_TRANSIT',
    occurredAt: '2026-10-02T09:30:00Z',
    note: null,
  },
];

describe('OrderTimeline', () => {
  it('lists the steps in chronological order even when the history arrives unsorted', () => {
    const items = Array.from(element(render(HISTORY)).querySelectorAll('li'));

    expect(
      items.map((item) => item.querySelector('.timeline__title')?.firstChild?.textContent?.trim()),
    ).toEqual(['Created', 'In transit', 'Failed']);
  });

  it('is an ordered list with an accessible name', () => {
    const list = element(render(HISTORY)).querySelector('ol');

    expect(list?.getAttribute('aria-label')).toBe('Status history');
  });

  it('marks only the latest step as the current status, in words', () => {
    const items = Array.from(element(render(HISTORY)).querySelectorAll('li'));

    expect(items.map((item) => item.classList.contains('timeline__step--current'))).toEqual([
      false,
      false,
      true,
    ]);
    expect(items[2].textContent).toContain('Current status');
    expect(items[0].textContent).not.toContain('Current status');
    expect(element(render(HISTORY)).querySelectorAll('.timeline__current').length).toBe(1);
  });

  it('shows the absolute time machine-readably and the relative time, and the note when present', () => {
    const items = Array.from(element(render(HISTORY)).querySelectorAll('li'));

    expect(items[0].querySelector('time')?.getAttribute('datetime')).toBe('2026-10-01T08:00:00Z');
    expect(items[0].textContent).toMatch(/\(\S[^)]*\)/);
    expect(items[2].textContent).toContain('Recipient unavailable');
    expect(items[1].querySelectorAll('p').length).toBe(2);
  });

  it('keeps the order of steps that happened at the same time', () => {
    const at = '2026-10-01T08:00:00Z';
    const items = Array.from(
      element(
        render([
          { fromStatus: null, toStatus: 'CREATED', occurredAt: at, note: null },
          { fromStatus: 'CREATED', toStatus: 'ASSIGNED', occurredAt: at, note: null },
        ]),
      ).querySelectorAll('.timeline__title'),
    );

    expect(items[0].textContent).toContain('Created');
    expect(items[1].textContent).toContain('Courier assigned');
  });

  it('says so when there is no history yet', () => {
    const fixture = render([]);

    expect(element(fixture).querySelector('ol')).toBeNull();
    expect(element(fixture).textContent).toContain('No status updates yet');
  });
});
