import { TestBed } from '@angular/core/testing';
import { ConfirmDialog } from './confirm-dialog';

describe('ConfirmDialog', () => {
  function create() {
    const fixture = TestBed.createComponent(ConfirmDialog);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      component: fixture.componentInstance,
      dialog: element.querySelector('dialog') as HTMLDialogElement,
      button: (text: string) =>
        Array.from(element.querySelectorAll('button')).find(
          (b) => b.textContent?.trim() === text,
        ) as HTMLButtonElement,
    };
  }

  afterEach(() => document.querySelectorAll('app-confirm-dialog').forEach((node) => node.remove()));

  it('opens as a modal dialog with the question and a labelled title', async () => {
    const { component, dialog } = create();

    const answer = component.ask({
      title: 'Revoke key?',
      message: 'Requests using it will fail.',
      confirmLabel: 'Revoke',
    });

    expect(dialog.open).toBeTrue();
    expect(dialog.matches(':modal')).toBeTrue();
    expect(dialog.textContent).toContain('Requests using it will fail.');
    expect(dialog.getAttribute('aria-labelledby')).toBe(
      dialog.querySelector('h2')?.id ?? 'missing',
    );
    dialog.close();
    expect(await answer).toBeFalse();
  });

  it('resolves true when confirmed', async () => {
    const { component, dialog, button } = create();

    const answer = component.ask({
      title: 'Delete?',
      message: 'Sure?',
      confirmLabel: 'Delete',
      danger: true,
    });
    expect(button('Delete').classList).toContain('btn-danger');
    button('Delete').click();

    expect(await answer).toBeTrue();
    expect(dialog.open).toBeFalse();
  });

  it('resolves false when cancelled', async () => {
    const { component, dialog, button } = create();

    const answer = component.ask({ title: 'Delete?', message: 'Sure?' });
    button('Cancel').click();

    expect(await answer).toBeFalse();
    expect(dialog.open).toBeFalse();
  });

  it('resolves false when the dialog is dismissed, for example with Escape', async () => {
    const { component, dialog } = create();

    const answer = component.ask({ title: 'Delete?', message: 'Sure?' });
    dialog.dispatchEvent(new Event('cancel'));
    dialog.close();

    expect(await answer).toBeFalse();
  });

  it('puts focus on the safe choice, Cancel', () => {
    const { component, button, dialog } = create();

    const answer = component.ask({ title: 'Delete?', message: 'Sure?' });

    expect(document.activeElement).toBe(button('Cancel'));
    dialog.close();
    return answer;
  });

  it('can ask several questions in a row', async () => {
    const { component, button } = create();

    const first = component.ask({ title: 'One', message: '?' });
    button('Confirm').click();
    expect(await first).toBeTrue();

    const second = component.ask({ title: 'Two', message: '?' });
    await new Promise((resolve) => setTimeout(resolve));
    button('Cancel').click();
    expect(await second).toBeFalse();
  });
});
