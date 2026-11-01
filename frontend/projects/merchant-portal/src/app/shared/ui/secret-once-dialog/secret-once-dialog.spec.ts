import { TestBed } from '@angular/core/testing';
import { SecretOnceDialog } from './secret-once-dialog';

const SECRET = 'sd_live_ab12cd34_s3cr3tvalue';

describe('SecretOnceDialog', () => {
  function create() {
    const fixture = TestBed.createComponent(SecretOnceDialog);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      element,
      component: fixture.componentInstance,
      dialog: element.querySelector('dialog') as HTMLDialogElement,
      done: () => element.querySelector('.dialog__actions button') as HTMLButtonElement,
      checkbox: () => element.querySelector('input[type="checkbox"]') as HTMLInputElement,
    };
  }

  function tick(fixture: { detectChanges(): void }): Promise<void> {
    fixture.detectChanges();
    return new Promise((resolve) => setTimeout(resolve));
  }

  afterEach(() =>
    document.querySelectorAll('app-secret-once-dialog').forEach((node) => node.remove()),
  );

  it('shows the secret and a warning that it is shown only once', () => {
    const { component, element, dialog } = create();

    void component.reveal({ title: 'API key created', label: 'API key', secret: SECRET });

    expect(dialog.open).toBeTrue();
    expect(element.querySelector('.secret__value')?.textContent).toBe(SECRET);
    expect(element.textContent).toContain('only time');
    expect(element.querySelector('app-copy-button')).not.toBeNull();
  });

  it('cannot be closed until the user confirms they saved the secret', async () => {
    const { fixture, component, dialog, done, checkbox } = create();
    let closed = false;
    void component
      .reveal({ title: 'API key created', label: 'API key', secret: SECRET })
      .then(() => (closed = true));

    expect(done().disabled).toBeTrue();
    done().click();
    await tick(fixture);
    expect(closed).toBeFalse();
    expect(dialog.open).toBeTrue();

    checkbox().click();
    await tick(fixture);
    expect(done().disabled).toBeFalse();
  });

  it('ignores Escape: a dismissed dialog opens again', async () => {
    const { component, dialog } = create();
    let closed = false;
    void component
      .reveal({ title: 'API key created', label: 'API key', secret: SECRET })
      .then(() => (closed = true));

    const cancel = new Event('cancel', { cancelable: true });
    dialog.dispatchEvent(cancel);
    expect(cancel.defaultPrevented).toBeTrue();

    const closeEvent = new Promise((resolve) =>
      dialog.addEventListener('close', resolve, { once: true }),
    );
    dialog.close();
    await closeEvent;

    expect(dialog.open).toBeTrue();
    expect(closed).toBeFalse();
  });

  it('resolves, closes and removes the secret from the page after the acknowledgement', async () => {
    const { fixture, component, element, dialog, done, checkbox } = create();
    const finished = component.reveal({
      title: 'API key created',
      label: 'API key',
      secret: SECRET,
    });

    checkbox().click();
    await tick(fixture);
    done().click();
    await finished;
    await tick(fixture);

    expect(dialog.open).toBeFalse();
    expect(element.textContent).not.toContain(SECRET);
    expect(element.querySelector('.secret__value')).toBeNull();
  });

  it('can show another secret afterwards, starting unacknowledged', async () => {
    const { fixture, component, done, checkbox } = create();
    const first = component.reveal({ title: 'One', label: 'API key', secret: SECRET });
    checkbox().click();
    await tick(fixture);
    done().click();
    await first;
    await tick(fixture);

    void component.reveal({ title: 'Two', label: 'client secret', secret: 'other' });
    await tick(fixture);

    expect(checkbox().checked).toBeFalse();
    expect(done().disabled).toBeTrue();
  });

  it('refuses to show a second secret while one is open', async () => {
    const { component, dialog } = create();
    void component.reveal({ title: 'One', label: 'API key', secret: SECRET });

    await expectAsync(
      component.reveal({ title: 'Two', label: 'API key', secret: 'x' }),
    ).toBeRejected();
    expect(dialog.open).toBeTrue();
  });
});
