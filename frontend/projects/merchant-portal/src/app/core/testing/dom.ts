import { ComponentFixture } from '@angular/core/testing';

/** Small DOM helpers for component specs. */
export function element(fixture: ComponentFixture<unknown>): HTMLElement {
  return fixture.nativeElement as HTMLElement;
}

export function query<T extends HTMLElement = HTMLElement>(
  fixture: ComponentFixture<unknown>,
  selector: string,
): T {
  const found = element(fixture).querySelector<T>(selector);
  if (!found) {
    throw new Error(`No element matches "${selector}"`);
  }
  return found;
}

/** The input found by its accessible label text (labels may contain the "(required)" suffix). */
export function inputByLabel(fixture: ComponentFixture<unknown>, label: string): HTMLInputElement {
  const match = Array.from(element(fixture).querySelectorAll('label')).find((candidate) =>
    candidate.textContent?.replace(/\s+/g, ' ').trim().startsWith(label),
  );
  if (!match) {
    throw new Error(`No label starts with "${label}"`);
  }
  return query<HTMLInputElement>(fixture, `#${match.htmlFor}`);
}

export function type(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

export function buttonByText(fixture: ComponentFixture<unknown>, text: string): HTMLButtonElement {
  const match = Array.from(element(fixture).querySelectorAll('button')).find((candidate) =>
    candidate.textContent?.replace(/\s+/g, ' ').trim().includes(text),
  );
  if (!match) {
    throw new Error(`No button contains "${text}"`);
  }
  return match;
}
