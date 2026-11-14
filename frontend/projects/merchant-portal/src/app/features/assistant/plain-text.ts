/**
 * The model often answers in Markdown. Replies are shown as plain text (never as HTML) so model output
 * cannot inject markup; this removes the formatting symbols so they do not appear literally.
 * Arithmetic such as `2 * 3` is left alone.
 */
export function plainText(reply: string): string {
  return reply
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/__(.+?)__/g, '$1')
    .replace(/(^|[\s(])\*(?!\s)(.+?)\*(?=[\s.,!?;:)]|$)/gm, '$1$2')
    .replace(/^\s*[*-]\s+/gm, '• ')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/`([^`]+)`/g, '$1');
}
