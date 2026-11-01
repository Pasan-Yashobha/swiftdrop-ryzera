/** Server limits for a bulk upload (CONTRACT 5.4: swiftdrop.bulk.max-rows and max-bytes). */
export const MAX_BULK_BYTES = 1024 * 1024;
export const MAX_BULK_ROWS = 500;

/** Why the file cannot be uploaded, or null when it can. The server checks again; this saves a round trip. */
export function bulkFileProblem(file: Pick<File, 'name' | 'size'>): string | null {
  if (!file.name.toLowerCase().endsWith('.csv')) {
    return 'Choose a .csv file. Export your spreadsheet as CSV (comma separated) first.';
  }
  if (file.size === 0) {
    return 'The file is empty.';
  }
  if (file.size > MAX_BULK_BYTES) {
    return `The file is ${formatBytes(file.size)}, which is over the 1 MiB limit. Split it into smaller files.`;
  }
  return null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const kib = bytes / 1024;
  return kib < 1024 ? `${kib.toFixed(1)} KiB` : `${(kib / 1024).toFixed(2)} MiB`;
}
