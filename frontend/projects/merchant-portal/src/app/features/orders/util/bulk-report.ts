import { BulkRowResult } from '../../../core/models/order.model';

/** Cells starting with one of these are read as formulas by spreadsheet programs (CSV injection). */
const FORMULA_START = /^[=+\-@\t\r]/;
const NEEDS_QUOTES = /[",\r\n]/;

/**
 * One CSV cell. Text that could be read as a formula gets a leading single quote, which spreadsheets
 * show as plain text; anything with a comma, quote or line break is quoted per RFC 4180.
 */
export function escapeCsvCell(value: string | number): string {
  const text = String(value);
  const safe = FORMULA_START.test(text) ? `'${text}` : text;
  return NEEDS_QUOTES.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
}

/** CSV report (row number and error messages) of the rows that could not be created. */
export function failedRowsCsv(results: readonly BulkRowResult[]): string {
  const lines = results
    .filter((result) => result.status === 'FAILED')
    .map((result) => [result.row, (result.errors ?? []).join('; ')].map(escapeCsvCell).join(','));
  return ['row,errors', ...lines].join('\r\n') + '\r\n';
}
