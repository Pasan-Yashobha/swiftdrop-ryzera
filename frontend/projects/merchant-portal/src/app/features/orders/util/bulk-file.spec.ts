import { bulkFileProblem, formatBytes, MAX_BULK_BYTES } from './bulk-file';

describe('bulkFileProblem', () => {
  it('accepts a non-empty CSV up to 1 MiB, whatever the case of the extension', () => {
    expect(bulkFileProblem({ name: 'orders.csv', size: 10 })).toBeNull();
    expect(bulkFileProblem({ name: 'ORDERS.CSV', size: MAX_BULK_BYTES })).toBeNull();
  });

  it('rejects other file types', () => {
    expect(bulkFileProblem({ name: 'orders.xlsx', size: 10 })).toContain('.csv');
    expect(bulkFileProblem({ name: 'orders.csv.exe', size: 10 })).toContain('.csv');
  });

  it('rejects empty and oversized files', () => {
    expect(bulkFileProblem({ name: 'a.csv', size: 0 })).toBe('The file is empty.');
    expect(bulkFileProblem({ name: 'a.csv', size: MAX_BULK_BYTES + 1 })).toContain('1 MiB limit');
  });
});

describe('formatBytes', () => {
  it('uses the largest sensible unit', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1.5 KiB');
    expect(formatBytes(1024 * 1024 + 1024 * 512)).toBe('1.50 MiB');
  });
});
