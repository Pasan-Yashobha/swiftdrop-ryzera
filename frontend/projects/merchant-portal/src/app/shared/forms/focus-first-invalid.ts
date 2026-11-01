/** Moves keyboard focus to the first invalid control inside `root`, so a failed submit lands on the problem. */
export function focusFirstInvalid(root: HTMLElement): void {
  root
    .querySelector<HTMLElement>('input.ng-invalid, select.ng-invalid, textarea.ng-invalid')
    ?.focus();
}
