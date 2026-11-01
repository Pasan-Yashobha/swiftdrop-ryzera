import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppError } from '../../../core/http/app-error';
import { SaveWebhookRequest, WebhookConfigured } from '../../../core/models/webhook.model';
import { element, inputByLabel, query, type } from '../../../core/testing/dom';
import { aConfiguredWebhook } from '../testing/developer-spec-helpers';
import { WebhookConfigForm } from './webhook-config-form';

describe('WebhookConfigForm', () => {
  let emitted: SaveWebhookRequest[];

  function create(config: WebhookConfigured | null = null): ComponentFixture<WebhookConfigForm> {
    const fixture = TestBed.createComponent(WebhookConfigForm);
    fixture.componentRef.setInput('config', config);
    fixture.componentInstance.submitted.subscribe((request) => emitted.push(request));
    fixture.detectChanges();
    return fixture;
  }

  function checkbox(fixture: ComponentFixture<unknown>, text: string): HTMLInputElement {
    const label = Array.from(element(fixture).querySelectorAll('label')).find((candidate) =>
      candidate.textContent?.includes(text),
    );
    if (!label) {
      throw new Error(`No checkbox labelled "${text}"`);
    }
    return label.querySelector('input') as HTMLInputElement;
  }

  function submit(fixture: ComponentFixture<unknown>): void {
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => (emitted = []));

  describe('a webhook that is not set up yet', () => {
    it('starts empty, active and subscribed to all events', () => {
      const fixture = create();

      expect(inputByLabel(fixture, 'Endpoint URL').value).toBe('');
      expect(checkbox(fixture, 'Active').checked).toBeTrue();
      expect(checkbox(fixture, 'All events').checked).toBeTrue();
      expect(checkbox(fixture, 'parcel.created').disabled).toBeTrue();
      expect(query(fixture, 'button[type="submit"]').textContent).toContain(
        'Save and create signing secret',
      );
    });

    it('lists exactly the events the backend allows', () => {
      const fixture = create();

      const types = Array.from(element(fixture).querySelectorAll('.events code')).map(
        (code) => code.textContent,
      );
      expect(types).toEqual([
        'parcel.created',
        'parcel.status.picked_up',
        'parcel.status.in_transit',
        'parcel.status.out_for_delivery',
        'parcel.status.delivered',
        'parcel.status.failed',
        'parcel.status.rescheduled',
        'parcel.status.returned',
        'parcel.cancelled',
      ]);
    });

    it('requires a URL', () => {
      const fixture = create();

      submit(fixture);

      expect(element(fixture).textContent).toContain('Enter the URL that should receive events.');
      expect(document.activeElement).toBe(inputByLabel(fixture, 'Endpoint URL'));
      expect(emitted).toEqual([]);
    });

    it('rejects an address that does not use a web scheme', () => {
      const fixture = create();

      type(inputByLabel(fixture, 'Endpoint URL'), 'ftp://shop.example.com/hooks');
      submit(fixture);

      expect(element(fixture).textContent).toContain('Enter a full https:// address');
      expect(emitted).toEqual([]);
    });

    it('rejects an address with a username or password in it', () => {
      const fixture = create();

      type(inputByLabel(fixture, 'Endpoint URL'), 'https://user:secret@shop.example.com/hooks');
      submit(fixture);

      expect(element(fixture).textContent).toContain('without a username or password');
      expect(emitted).toEqual([]);
    });

    it('rejects an address longer than 500 characters', () => {
      const fixture = create();

      type(inputByLabel(fixture, 'Endpoint URL'), `https://shop.example.com/${'a'.repeat(500)}`);
      submit(fixture);

      expect(element(fixture).textContent).toContain('at most 500 characters');
      expect(emitted).toEqual([]);
    });

    it('submits the trimmed address for all events', () => {
      const fixture = create();

      type(inputByLabel(fixture, 'Endpoint URL'), '  https://shop.example.com/hooks/swiftdrop ');
      submit(fixture);

      expect(emitted).toEqual([
        { url: 'https://shop.example.com/hooks/swiftdrop', active: true, events: ['*'] },
      ]);
    });

    it('submits an inactive webhook when the switch is off', () => {
      const fixture = create();
      type(inputByLabel(fixture, 'Endpoint URL'), 'https://shop.example.com/hooks');

      checkbox(fixture, 'Active').click();
      submit(fixture);

      expect(emitted[0].active).toBeFalse();
    });
  });

  describe('choosing events', () => {
    /** Turns "all events" off and ticks the given events, as a user would. */
    function pickEvents(fixture: ComponentFixture<WebhookConfigForm>, ...types: string[]): void {
      checkbox(fixture, 'All events').click();
      fixture.detectChanges();
      for (const eventType of types) {
        checkbox(fixture, eventType).click();
      }
    }

    it('lets the user pick specific events once "all events" is off', () => {
      const fixture = create();
      type(inputByLabel(fixture, 'Endpoint URL'), 'https://shop.example.com/hooks');
      expect(checkbox(fixture, 'parcel.created').disabled).toBeTrue();

      pickEvents(fixture, 'parcel.status.delivered', 'parcel.created');
      submit(fixture);

      expect(checkbox(fixture, 'parcel.created').disabled).toBeFalse();
      expect(emitted[0].events).toEqual(['parcel.created', 'parcel.status.delivered']);
    });

    it('never sends "*" together with specific events', () => {
      const fixture = create();
      type(inputByLabel(fixture, 'Endpoint URL'), 'https://shop.example.com/hooks');
      pickEvents(fixture, 'parcel.cancelled');

      checkbox(fixture, 'All events').click();
      submit(fixture);

      expect(emitted[0].events).toEqual(['*']);
    });

    it('can unselect an event again', () => {
      const fixture = create();
      type(inputByLabel(fixture, 'Endpoint URL'), 'https://shop.example.com/hooks');
      pickEvents(fixture, 'parcel.created', 'parcel.cancelled');

      checkbox(fixture, 'parcel.created').click();
      submit(fixture);

      expect(emitted[0].events).toEqual(['parcel.cancelled']);
    });

    it('asks for at least one event', () => {
      const fixture = create();
      type(inputByLabel(fixture, 'Endpoint URL'), 'https://shop.example.com/hooks');
      pickEvents(fixture);

      submit(fixture);

      expect(query(fixture, '.events-error').textContent).toContain('Select at least one event');
      expect(emitted).toEqual([]);
    });
  });

  describe('an existing webhook', () => {
    it('shows the saved settings', () => {
      const fixture = create(
        aConfiguredWebhook({
          url: 'https://old.example.com/hook',
          active: false,
          events: ['parcel.created', 'parcel.cancelled'],
        }),
      );

      expect(inputByLabel(fixture, 'Endpoint URL').value).toBe('https://old.example.com/hook');
      expect(checkbox(fixture, 'Active').checked).toBeFalse();
      expect(checkbox(fixture, 'All events').checked).toBeFalse();
      expect(checkbox(fixture, 'parcel.created').checked).toBeTrue();
      expect(checkbox(fixture, 'parcel.cancelled').checked).toBeTrue();
      expect(checkbox(fixture, 'parcel.status.delivered').checked).toBeFalse();
      expect(query(fixture, 'button[type="submit"]').textContent).toContain('Save changes');
    });

    it('shows "all events" for a catch-all subscription', () => {
      const fixture = create(aConfiguredWebhook({ events: ['*'] }));

      expect(checkbox(fixture, 'All events').checked).toBeTrue();
      expect(checkbox(fixture, 'parcel.created').checked).toBeFalse();
    });

    it('ignores events it does not know about when saving', () => {
      const fixture = create(aConfiguredWebhook({ events: ['parcel.created', 'parcel.assigned'] }));

      submit(fixture);

      expect(emitted[0].events).toEqual(['parcel.created']);
    });

    it('goes back to the defaults when the webhook is deleted', () => {
      const fixture = create(aConfiguredWebhook({ active: false }));

      fixture.componentRef.setInput('config', null);
      fixture.detectChanges();

      expect(inputByLabel(fixture, 'Endpoint URL').value).toBe('');
      expect(checkbox(fixture, 'Active').checked).toBeTrue();
    });
  });

  describe('saving', () => {
    it('disables the button while saving', () => {
      const fixture = create();
      fixture.componentRef.setInput('busy', true);
      fixture.detectChanges();

      const button = query<HTMLButtonElement>(fixture, 'button[type="submit"]');
      expect(button.disabled).toBeTrue();
      expect(button.textContent).toContain('Saving...');
    });

    it('shows the server message and marks the field it names', () => {
      const fixture = create();
      type(inputByLabel(fixture, 'Endpoint URL'), 'https://shop.example.com/hooks');

      fixture.componentRef.setInput(
        'submitError',
        new AppError(422, 'WEBHOOK_URL_REJECTED', 'That address is not allowed.', [
          { field: 'url', message: 'Private addresses are not allowed.' },
        ]),
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('That address is not allowed.');
      expect(element(fixture).textContent).toContain('Private addresses are not allowed.');
      expect(inputByLabel(fixture, 'Endpoint URL').getAttribute('aria-invalid')).toBe('true');
    });
  });
});
