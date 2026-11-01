import { afterNextRender, Injector } from '@angular/core';

/**
 * Moves focus to an element once the pending change detection has rendered it, for example the heading of a
 * result that replaced the control the user just activated. The target needs `tabindex="-1"` unless it is
 * focusable already.
 */
export function focusAfterRender(injector: Injector, target: () => HTMLElement | undefined): void {
  afterNextRender(() => target()?.focus(), { injector });
}
