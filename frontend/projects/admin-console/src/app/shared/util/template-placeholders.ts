const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_.]+)\s*\}\}/g;

/** The distinct `{{placeholder}}` names used in a template text, in order of first use. */
export function templatePlaceholders(text: string | null | undefined): string[] {
  if (!text) {
    return [];
  }
  const names = Array.from(text.matchAll(PLACEHOLDER), (match) => match[1]);
  return [...new Set(names)];
}
