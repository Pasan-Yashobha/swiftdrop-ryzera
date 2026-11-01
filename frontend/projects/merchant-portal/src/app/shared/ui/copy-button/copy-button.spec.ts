import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { CopyButton } from './copy-button';

describe('CopyButton', () => {
  function create(label?: string) {
    const fixture = TestBed.createComponent(CopyButton);
    fixture.componentRef.setInput('value', 'whsec_value');
    if (label) {
      fixture.componentRef.setInput('label', label);
    }
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    return { fixture, element, button: element.querySelector('button') as HTMLButtonElement };
  }

  it('copies the value and confirms it, then reverts', fakeAsync(() => {
    const writeText = jasmine.createSpy('writeText').and.resolveTo();
    spyOnProperty(navigator, 'clipboard', 'get').and.returnValue({
      writeText,
    } as unknown as Clipboard);
    const { fixture, element, button } = create('Copy secret');
    expect(button.textContent).toContain('Copy secret');

    button.click();
    flushMicrotasks();
    fixture.detectChanges();

    expect(writeText).toHaveBeenCalledWith('whsec_value');
    expect(button.textContent).toContain('Copied');
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Copied to clipboard');

    tick(2000);
    fixture.detectChanges();
    expect(button.textContent).toContain('Copy secret');
  }));

  it('reports a failure instead of pretending it worked', fakeAsync(() => {
    spyOnProperty(navigator, 'clipboard', 'get').and.returnValue({
      writeText: () => Promise.reject(new Error('denied')),
    } as unknown as Clipboard);
    const { fixture, element, button } = create();

    button.click();
    flushMicrotasks();
    fixture.detectChanges();

    expect(button.textContent).toContain('Copy failed');
    expect(element.querySelector('[role="status"]')?.textContent).toContain('copy it manually');
    tick(2000);
  }));
});
