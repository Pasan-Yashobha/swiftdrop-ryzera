import { TestBed } from '@angular/core/testing';
import { RotateKeyDialog } from './rotate-key-dialog';

describe('RotateKeyDialog', () => {
  function create() {
    const fixture = TestBed.createComponent(RotateKeyDialog);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const input = () => element.querySelector('input') as HTMLInputElement;
    return {
      fixture,
      component: fixture.componentInstance,
      dialog: element.querySelector('dialog') as HTMLDialogElement,
      input,
      button: (text: string) =>
        Array.from(element.querySelectorAll('button')).find(
          (candidate) => candidate.textContent?.trim() === text,
        ) as HTMLButtonElement,
      enter: (value: string) => {
        input().value = value;
        input().dispatchEvent(new Event('input'));
        fixture.detectChanges();
      },
    };
  }

  afterEach(() => document.querySelectorAll('app-rotate-key-dialog').forEach((n) => n.remove()));

  it('names the key and offers the default 24 hour grace period', () => {
    const { component, dialog, input } = create();

    void component.ask('Shop backend');

    expect(dialog.open).toBeTrue();
    expect(dialog.querySelector('h2')?.textContent).toContain('Shop backend');
    expect(input().value).toBe('24');
    expect(dialog.textContent).toContain('0 revokes the old key immediately');
  });

  it('resolves with the chosen number of hours', async () => {
    const { fixture, component, button, enter } = create();
    const answer = component.ask('Shop backend');

    enter('48');
    button('Rotate key').click();
    fixture.detectChanges();

    expect(await answer).toBe(48);
  });

  it('accepts 0 and 72 as the limits', async () => {
    const { component, button, enter } = create();

    const none = component.ask('Shop backend');
    enter('0');
    button('Rotate key').click();
    expect(await none).toBe(0);

    const max = component.ask('Shop backend');
    enter('72');
    button('Rotate key').click();
    expect(await max).toBe(72);
  });

  it('refuses an out of range or fractional value and keeps asking', () => {
    const { fixture, component, dialog, button, enter } = create();
    let answered = false;
    void component.ask('Shop backend').then(() => (answered = true));

    enter('73');
    button('Rotate key').click();
    fixture.detectChanges();
    expect(dialog.textContent).toContain('at most 72 hours');

    enter('-1');
    button('Rotate key').click();
    fixture.detectChanges();
    expect(dialog.textContent).toContain('cannot be negative');

    enter('1.5');
    button('Rotate key').click();
    fixture.detectChanges();
    expect(dialog.textContent).toContain('whole number');

    enter('');
    button('Rotate key').click();
    fixture.detectChanges();
    expect(dialog.textContent).toContain('between 0 and 72');

    expect(dialog.open).toBeTrue();
    expect(answered).toBeFalse();
  });

  it('resolves null when cancelled', async () => {
    const { component, dialog, button } = create();
    const answer = component.ask('Shop backend');

    button('Cancel').click();

    expect(await answer).toBeNull();
    expect(dialog.open).toBeFalse();
  });

  it('resolves null when dismissed with Escape', async () => {
    const { component, dialog } = create();
    const answer = component.ask('Shop backend');

    dialog.close();

    expect(await answer).toBeNull();
  });

  it('starts from the default again for the next key', async () => {
    const { component, dialog, input, button, enter } = create();
    const first = component.ask('One');
    enter('5');
    button('Cancel').click();
    await first;

    void component.ask('Two');

    expect(dialog.querySelector('h2')?.textContent).toContain('Two');
    expect(input().value).toBe('24');
  });
});
