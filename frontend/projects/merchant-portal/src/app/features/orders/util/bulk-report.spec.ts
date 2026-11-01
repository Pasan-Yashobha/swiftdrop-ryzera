import { BulkRowResult } from '../../../core/models/order.model';
import { escapeCsvCell, failedRowsCsv } from './bulk-report';

describe('escapeCsvCell', () => {
  it('leaves plain text alone', () => {
    expect(escapeCsvCell('dropoffCity: must not be blank')).toBe('dropoffCity: must not be blank');
    expect(escapeCsvCell(12)).toBe('12');
  });

  it('prefixes a single quote when a cell could be read as a formula', () => {
    expect(escapeCsvCell('=HYPERLINK("http://evil.example")')).toBe(
      '"\'=HYPERLINK(""http://evil.example"")"',
    );
    expect(escapeCsvCell('+94771234567')).toBe("'+94771234567");
    expect(escapeCsvCell('-2+3')).toBe("'-2+3");
    expect(escapeCsvCell('@SUM(A1)')).toBe("'@SUM(A1)");
    expect(escapeCsvCell('\t=1+1')).toBe("'\t=1+1");
    expect(escapeCsvCell('\r=1+1')).toBe('"\'\r=1+1"');
    expect(escapeCsvCell(-5)).toBe("'-5");
  });

  it('does not touch a formula character in the middle of the text', () => {
    expect(escapeCsvCell('weight = 5')).toBe('weight = 5');
  });

  it('quotes cells with commas, quotes or line breaks', () => {
    expect(escapeCsvCell('a,b')).toBe('"a,b"');
    expect(escapeCsvCell('say "hi"')).toBe('"say ""hi"""');
    expect(escapeCsvCell('one\ntwo')).toBe('"one\ntwo"');
  });
});

describe('failedRowsCsv', () => {
  const results: BulkRowResult[] = [
    { row: 2, status: 'CREATED', trackingNo: 'SD24000123', orderId: 'o-1' },
    { row: 3, status: 'FAILED', errors: ['dropoffCity: must not be blank', 'size: invalid'] },
    { row: 4, status: 'FAILED', errors: ['=cmd|calc'] },
    { row: 5, status: 'FAILED' },
  ];

  it('lists only the failed rows, with all messages of a row in one cell', () => {
    expect(failedRowsCsv(results)).toBe(
      [
        'row,errors',
        '3,dropoffCity: must not be blank; size: invalid',
        "4,'=cmd|calc",
        '5,',
        '',
      ].join('\r\n'),
    );
  });

  it('has only the header when nothing failed', () => {
    expect(failedRowsCsv([results[0]])).toBe('row,errors\r\n');
  });
});
