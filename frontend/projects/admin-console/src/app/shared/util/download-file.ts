/** Saves a Blob through a temporary link, which works for generated content without a server round trip. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Some browsers start reading the blob after the click returns.
  setTimeout(() => URL.revokeObjectURL(url));
}
