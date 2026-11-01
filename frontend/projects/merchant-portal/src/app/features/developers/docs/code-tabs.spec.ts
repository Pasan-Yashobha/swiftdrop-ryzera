import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeSample } from './api-docs.samples';
import { CodeTabs } from './code-tabs';

const SAMPLES: readonly CodeSample[] = [
  { id: 'node', label: 'Node.js', code: 'console.log("node")' },
  { id: 'python', label: 'Python', code: 'print("python")' },
  { id: 'java', label: 'Java', code: 'System.out.println("java");' },
];

describe('CodeTabs', () => {
  let fixture: ComponentFixture<CodeTabs>;

  beforeEach(() => {
    fixture = TestBed.createComponent(CodeTabs);
    fixture.componentRef.setInput('samples', SAMPLES);
    fixture.componentRef.setInput('label', 'Example language');
    fixture.detectChanges();
  });

  const root = () => fixture.nativeElement as HTMLElement;
  const tabs = () => Array.from(root().querySelectorAll<HTMLButtonElement>('[role="tab"]'));
  const panels = () => Array.from(root().querySelectorAll<HTMLElement>('[role="tabpanel"]'));
  const selected = () => tabs().findIndex((tab) => tab.getAttribute('aria-selected') === 'true');

  function press(tab: HTMLElement, key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    tab.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  it('is a labelled tab list with one tab per sample', () => {
    const list = root().querySelector('[role="tablist"]');

    expect(list?.getAttribute('aria-label')).toBe('Example language');
    expect(tabs().map((tab) => tab.textContent?.trim())).toEqual(['Node.js', 'Python', 'Java']);
  });

  it('selects the first tab and shows only its panel', () => {
    expect(selected()).toBe(0);
    expect(panels().map((panel) => panel.hidden)).toEqual([false, true, true]);
    expect(panels()[0].textContent).toContain('console.log("node")');
  });

  it('links every tab to its panel both ways', () => {
    tabs().forEach((tab, index) => {
      expect(tab.getAttribute('aria-controls')).toBe(panels()[index].id);
      expect(panels()[index].getAttribute('aria-labelledby')).toBe(tab.id);
    });
    expect(new Set(tabs().map((tab) => tab.id)).size).toBe(3);
  });

  it('puts only the selected tab in the tab order', () => {
    expect(tabs().map((tab) => tab.tabIndex)).toEqual([0, -1, -1]);
  });

  it('switches panels when a tab is clicked', () => {
    tabs()[1].click();
    fixture.detectChanges();

    expect(selected()).toBe(1);
    expect(panels().map((panel) => panel.hidden)).toEqual([true, false, true]);
    expect(tabs().map((tab) => tab.tabIndex)).toEqual([-1, 0, -1]);
  });

  it('moves to the next tab with the right arrow, focuses it and wraps around', () => {
    const event = press(tabs()[0], 'ArrowRight');

    expect(event.defaultPrevented).toBeTrue();
    expect(selected()).toBe(1);
    expect(document.activeElement).toBe(tabs()[1]);
    press(tabs()[1], 'ArrowRight');
    press(tabs()[2], 'ArrowRight');
    expect(selected()).toBe(0);
    expect(document.activeElement).toBe(tabs()[0]);
  });

  it('moves to the previous tab with the left arrow and wraps around', () => {
    press(tabs()[0], 'ArrowLeft');

    expect(selected()).toBe(2);
    expect(document.activeElement).toBe(tabs()[2]);
    press(tabs()[2], 'ArrowLeft');
    expect(selected()).toBe(1);
  });

  it('jumps to the first and last tab with Home and End', () => {
    press(tabs()[0], 'End');
    expect(selected()).toBe(2);
    expect(document.activeElement).toBe(tabs()[2]);

    press(tabs()[2], 'Home');
    expect(selected()).toBe(0);
    expect(document.activeElement).toBe(tabs()[0]);
  });

  it('leaves other keys alone', () => {
    const event = press(tabs()[0], 'a');

    expect(event.defaultPrevented).toBeFalse();
    expect(selected()).toBe(0);
  });

  it('gives each panel a copy button for its own code', () => {
    const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
    tabs()[2].click();
    fixture.detectChanges();

    panels()[2].querySelector<HTMLButtonElement>('app-copy-button button')?.click();

    expect(writeText).toHaveBeenCalledWith('System.out.println("java");');
  });
});
