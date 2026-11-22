/**
 * Indents a raw JSON string for display in a <pre>. Anything that is not valid JSON is returned unchanged,
 * so a malformed payload is still shown exactly as it was sent. The result is plain text, never HTML.
 */
export function prettyJson(raw: string | null | undefined): string {
  if (raw === null || raw === undefined) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
