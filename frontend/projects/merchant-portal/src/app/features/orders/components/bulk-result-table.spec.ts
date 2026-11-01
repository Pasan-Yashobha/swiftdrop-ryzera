import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BulkRowResult } from '../../../core/models/order.model';
import { element, query } from '../../../core/testing/dom';
import { BulkResultTable } from './bulk-result-table';

const RESULTS: BulkRowResult[] = [
  { row: 2, status: 'CREATED', trackingNo: 'SD24000123', orderId: 'order-2' },
  { row: 3, status: 'FAILED', errors: ['dropoffCity: must not be blank', 'size: must be SMALL'] },
  { row: 4, status: 'FAILED' },
];

function render(results: BulkRowResult[]) {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  const fixture = TestBed.createComponent(BulkResultTable);
  fixture.componentRef.setInput('results', results);
  fixture.detectChanges();
  return fixture;
}

function rows(root: HTMLElement): string[][] {
  return Array.from(root.querySelectorAll('tbody tr')).map((row) =>
    Array.from(row.querySelectorAll('td')).map((cell) => {
      const messages = Array.from(cell.querySelectorAll('li'));
      return messages.length > 0
        ? messages.map((message) => message.textContent).join('; ')
        : (cell.textContent?.trim() ?? '');
    }),
  );
}

describe('BulkResultTable', () => {
  it('shows one row per result with its outcome and details', () => {
    const fixture = render(RESULTS);

    expect(rows(element(fixture))).toEqual([
      ['2', '●Created', 'SD24000123'],
      ['3', '✕Failed', 'dropoffCity: must not be blank; size: must be SMALL'],
      ['4', '✕Failed', 'This row could not be processed.'],
    ]);
  });

  it('links a created row to its order', () => {
    const link = query<HTMLAnchorElement>(render(RESULTS), 'tbody a');

    expect(link.textContent).toBe('SD24000123');
    expect(link.getAttribute('href')).toBe('/orders/order-2');
  });

  it('lists each error of a failed row separately', () => {
    const errors = element(render(RESULTS)).querySelectorAll('tbody ul li');

    expect(Array.from(errors).map((item) => item.textContent)).toEqual([
      'dropoffCity: must not be blank',
      'size: must be SMALL',
    ]);
  });

  it('filters to the failed rows on request and back', () => {
    const fixture = render(RESULTS);
    const checkbox = query<HTMLInputElement>(fixture, 'input[type="checkbox"]');

    checkbox.click();
    fixture.detectChanges();
    expect(rows(element(fixture)).map((row) => row[0])).toEqual(['3', '4']);

    checkbox.click();
    fixture.detectChanges();
    expect(rows(element(fixture)).map((row) => row[0])).toEqual(['2', '3', '4']);
  });

  it('offers the failed-only filter only when something failed', () => {
    const fixture = render([RESULTS[0]]);

    expect(element(fixture).querySelector('input[type="checkbox"]')).toBeNull();
  });

  it('gives the table a hidden caption and column headers', () => {
    const table = element(render(RESULTS));

    expect(table.querySelector('caption')?.textContent).toContain('Result for each row');
    expect(
      Array.from(table.querySelectorAll('th[scope="col"]')).map((th) => th.textContent),
    ).toEqual(['Row', 'Result', 'Details']);
  });
});
