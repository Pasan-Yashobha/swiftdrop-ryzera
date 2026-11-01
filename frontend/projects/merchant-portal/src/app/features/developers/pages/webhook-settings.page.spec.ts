import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { WebhookConfig } from '../../../core/models/webhook.model';
import { buttonByText, element, inputByLabel, query, type } from '../../../core/testing/dom';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import {
  acknowledgeSecret,
  aConfiguredWebhook,
  aDeliveryPage,
  confirmButton,
  retainsSecret,
  secretDialog,
  settle,
} from '../testing/developer-spec-helpers';
import { WebhookSettingsPage } from './webhook-settings.page';

const WEBHOOK_URL = '/api/v1/merchants/me/webhook';
const SECRET = 'whsec_Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyMDE';
const NOT_CONFIGURED: WebhookConfig = { configured: false };

describe('WebhookSettingsPage', () => {
  let http: HttpTestingController;
  let toasts: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    spyOn(toasts, 'success');
  });

  afterEach(() => http.verify());

  const configRequest = () => http.expectOne((r) => r.method === 'GET' && r.url === WEBHOOK_URL);
  const deliveriesRequest = () =>
    http.expectOne((r) => r.method === 'GET' && r.url === `${WEBHOOK_URL}/deliveries`);
  const request = (method: string, path = '') =>
    http.expectOne((r) => r.method === method && r.url === `${WEBHOOK_URL}${path}`);

  function create(config: WebhookConfig = NOT_CONFIGURED): ComponentFixture<WebhookSettingsPage> {
    const fixture = TestBed.createComponent(WebhookSettingsPage);
    fixture.detectChanges();
    configRequest().flush(config);
    fixture.detectChanges();
    if (config.configured) {
      deliveriesRequest().flush(aDeliveryPage([]));
      fixture.detectChanges();
    }
    return fixture;
  }

  function saveWith(fixture: ComponentFixture<WebhookSettingsPage>, url: string): void {
    type(inputByLabel(fixture, 'Endpoint URL'), url);
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  describe('loading', () => {
    it('shows a placeholder while the settings load', () => {
      const fixture = TestBed.createComponent(WebhookSettingsPage);
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Loading webhook settings');
      configRequest().flush(NOT_CONFIGURED);
    });

    it('shows the load error and retries', () => {
      const fixture = TestBed.createComponent(WebhookSettingsPage);
      fixture.detectChanges();
      configRequest().flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('temporarily unavailable');
      buttonByText(fixture, 'Try again').click();
      configRequest().flush(NOT_CONFIGURED);
      fixture.detectChanges();

      expect(element(fixture).querySelector('form')).not.toBeNull();
    });

    it('explains that the business is not approved', () => {
      const fixture = TestBed.createComponent(WebhookSettingsPage);
      fixture.detectChanges();
      configRequest().flush(
        { code: 'MERCHANT_NOT_APPROVED', message: 'Your merchant account is not approved.' },
        { status: 403, statusText: 'Forbidden' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Your merchant account is not approved.');
      expect(element(fixture).querySelector('a[href="/status"]')).not.toBeNull();
    });

    it('explains signature verification before anything else', () => {
      const fixture = create();

      const explainer = query(fixture, '[aria-labelledby="verify-heading"]');
      expect(explainer.textContent).toContain('Always verify the signature');
      expect(explainer.textContent).toContain('timestamp + "." + body');
      expect(explainer.textContent).toContain('constant-time');
      expect(explainer.textContent).toContain('5 minutes');
      expect(explainer.querySelector('a')?.getAttribute('href')).toBe(
        '/developers/docs#verify-signatures',
      );
    });
  });

  describe('a webhook that is not set up yet', () => {
    it('offers only the configuration form', () => {
      const fixture = create();

      expect(element(fixture).querySelector('form')).not.toBeNull();
      expect(element(fixture).textContent).not.toContain('Send test event');
      expect(element(fixture).textContent).not.toContain('Delivery log');
    });

    it('saves the configuration and shows the new signing secret once', async () => {
      const fixture = create();
      saveWith(fixture, 'https://shop.example.com/hooks/swiftdrop');

      const save = request('PUT');
      expect(save.request.body).toEqual({
        url: 'https://shop.example.com/hooks/swiftdrop',
        active: true,
        events: ['*'],
      });
      save.flush({ ...aConfiguredWebhook(), secret: SECRET });
      await settle(fixture);
      deliveriesRequest().flush(aDeliveryPage([]));
      await settle(fixture);

      const dialog = secretDialog(fixture);
      expect(dialog.open).toBeTrue();
      expect(dialog.textContent).toContain('Webhook signing secret');
      expect(dialog.textContent).toContain(SECRET);
      expect(element(fixture).textContent).toContain('whsec_ab12...');
    });

    it('drops the secret from the page and the component once the dialog is closed', async () => {
      const fixture = create();
      saveWith(fixture, 'https://shop.example.com/hooks/swiftdrop');
      request('PUT').flush({ ...aConfiguredWebhook(), secret: SECRET });
      await settle(fixture);
      deliveriesRequest().flush(aDeliveryPage([]));
      await settle(fixture);

      await acknowledgeSecret(fixture);

      expect(secretDialog(fixture).open).toBeFalse();
      expect(element(fixture).textContent).not.toContain(SECRET);
      expect(retainsSecret(fixture.componentInstance, SECRET)).toBeFalse();
    });

    it('keeps the form and shows the reason when the address is rejected', () => {
      const fixture = create();
      saveWith(fixture, 'https://10.0.0.5/hooks');

      request('PUT').flush(
        { code: 'WEBHOOK_URL_REJECTED', message: 'That address points to a private network.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('That address points to a private network.');
      expect(inputByLabel(fixture, 'Endpoint URL').value).toBe('https://10.0.0.5/hooks');
      expect(element(fixture).textContent).not.toContain('Send test event');
    });

    it('explains that the business is not approved when saving is refused', () => {
      const fixture = create();
      saveWith(fixture, 'https://shop.example.com/hooks');

      request('PUT').flush(
        { code: 'MERCHANT_NOT_APPROVED', message: 'Your merchant account is suspended.' },
        { status: 403, statusText: 'Forbidden' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Your merchant account is suspended.');
      expect(element(fixture).querySelector('a[href="/status"]')).not.toBeNull();
    });
  });

  describe('a configured webhook', () => {
    it('shows the settings and the secret hint, never the secret', () => {
      const fixture = create(aConfiguredWebhook());

      expect(inputByLabel(fixture, 'Endpoint URL').value).toBe(
        'https://shop.example.com/hooks/swiftdrop',
      );
      const secretSection = query(fixture, '[aria-labelledby="secret-heading"]');
      expect(secretSection.textContent).toContain('whsec_ab12...');
      expect(secretSection.textContent).toContain('Never tested');
      expect(element(fixture).textContent).toContain('Delivery log');
    });

    it('shows when the last test ran and what it returned', () => {
      const fixture = create(
        aConfiguredWebhook({
          lastTestAt: new Date(Date.now() - 3 * 60_000).toISOString(),
          lastTestStatus: 'DELIVERED (200)',
        }),
      );

      const text = query(fixture, '[aria-labelledby="secret-heading"]').textContent;
      expect(text).toContain('3 minutes ago');
      expect(text).toContain('DELIVERED (200)');
    });

    it('saves changes without showing a secret', async () => {
      const fixture = create(aConfiguredWebhook());
      saveWith(fixture, 'https://shop.example.com/new-hook');

      const save = request('PUT');
      expect(save.request.body.url).toBe('https://shop.example.com/new-hook');
      save.flush(aConfiguredWebhook({ url: 'https://shop.example.com/new-hook' }));
      await settle(fixture);

      expect(secretDialog(fixture).open).toBeFalse();
      expect(toasts.success).toHaveBeenCalledWith('Webhook settings saved.');
      expect(inputByLabel(fixture, 'Endpoint URL').value).toBe('https://shop.example.com/new-hook');
    });
  });

  describe('sending a test event', () => {
    function resultRegion(fixture: ComponentFixture<WebhookSettingsPage>): HTMLElement {
      return query(fixture, '[data-testid="test-result"]');
    }

    it('announces the result in a live region, starting empty', () => {
      const fixture = create(aConfiguredWebhook());

      expect(resultRegion(fixture).getAttribute('aria-live')).toBe('polite');
      expect(resultRegion(fixture).textContent?.trim()).toBe('');
    });

    it('reports a delivered test with its status and duration, then refreshes the settings', () => {
      const fixture = create(aConfiguredWebhook());

      buttonByText(fixture, 'Send test event').click();
      fixture.detectChanges();
      expect(buttonByText(fixture, 'Sending...').disabled).toBeTrue();
      request('POST', '/test').flush({
        delivered: true,
        httpStatus: 200,
        durationMs: 123,
        error: null,
      });
      configRequest().flush(
        aConfiguredWebhook({ lastTestAt: new Date().toISOString(), lastTestStatus: 'DELIVERED' }),
      );
      fixture.detectChanges();

      const text = resultRegion(fixture).textContent ?? '';
      expect(text).toContain('Delivered');
      expect(text).toContain('HTTP 200');
      expect(text).toContain('123 ms');
      expect(resultRegion(fixture).querySelector('.alert--success')).not.toBeNull();
      expect(query(fixture, '[aria-labelledby="secret-heading"]').textContent).toContain(
        'just now',
      );
    });

    it('reports a failed test with the HTTP status and the error', () => {
      const fixture = create(aConfiguredWebhook());

      buttonByText(fixture, 'Send test event').click();
      request('POST', '/test').flush({
        delivered: false,
        httpStatus: 500,
        durationMs: 480,
        error: 'Your endpoint did not return a 2xx status.',
      });
      configRequest().flush(aConfiguredWebhook());
      fixture.detectChanges();

      const text = resultRegion(fixture).textContent ?? '';
      expect(text).toContain('Not delivered after 480 ms');
      expect(text).toContain('HTTP 500');
      expect(text).toContain('Your endpoint did not return a 2xx status.');
      expect(resultRegion(fixture).querySelector('.alert--danger')).not.toBeNull();
    });

    it('reports a test that got no answer at all', () => {
      const fixture = create(aConfiguredWebhook());

      buttonByText(fixture, 'Send test event').click();
      request('POST', '/test').flush({
        delivered: false,
        httpStatus: null,
        durationMs: 5000,
        error: 'The endpoint did not answer within 5 seconds.',
      });
      configRequest().flush(aConfiguredWebhook());
      fixture.detectChanges();

      const text = resultRegion(fixture).textContent ?? '';
      expect(text).toContain('Not delivered after 5000 ms');
      expect(text).not.toContain('HTTP');
      expect(text).toContain('did not answer within 5 seconds');
    });

    it('clears the previous result while a new test runs', () => {
      const fixture = create(aConfiguredWebhook());
      buttonByText(fixture, 'Send test event').click();
      request('POST', '/test').flush({
        delivered: true,
        httpStatus: 204,
        durationMs: 9,
        error: null,
      });
      configRequest().flush(aConfiguredWebhook());
      fixture.detectChanges();

      buttonByText(fixture, 'Send test event').click();
      fixture.detectChanges();

      expect(resultRegion(fixture).textContent?.trim()).toBe('');
      request('POST', '/test').flush({
        delivered: true,
        httpStatus: 204,
        durationMs: 9,
        error: null,
      });
      configRequest().flush(aConfiguredWebhook());
    });

    it('shows the error when the server cannot run the test', () => {
      const fixture = create(aConfiguredWebhook());

      buttonByText(fixture, 'Send test event').click();
      request('POST', '/test').flush(
        { code: 'WEBHOOK_NOT_CONFIGURED', message: 'Set up a webhook first.' },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Set up a webhook first.');
      expect(resultRegion(fixture).textContent?.trim()).toBe('');
    });
  });

  describe('rotating the secret', () => {
    async function askToRotate(fixture: ComponentFixture<WebhookSettingsPage>): Promise<void> {
      buttonByText(fixture, 'Rotate secret').click();
      await settle(fixture);
    }

    it('warns that the current secret stops working immediately', async () => {
      const fixture = create(aConfiguredWebhook());

      await askToRotate(fixture);

      const dialog = query<HTMLDialogElement>(fixture, 'app-confirm-dialog dialog');
      expect(dialog.open).toBeTrue();
      expect(dialog.textContent).toContain('stops working immediately');
    });

    it('shows the new secret once and refreshes the hint', async () => {
      const fixture = create(aConfiguredWebhook());
      await askToRotate(fixture);

      confirmButton(fixture, 'Rotate secret').click();
      await settle(fixture);
      request('POST', '/rotate-secret').flush({ secret: SECRET });
      configRequest().flush(aConfiguredWebhook({ secretHint: 'whsec_new9...' }));
      await settle(fixture);

      const dialog = secretDialog(fixture);
      expect(dialog.textContent).toContain('New webhook signing secret');
      expect(dialog.textContent).toContain(SECRET);
      expect(element(fixture).textContent).toContain('whsec_new9...');

      await acknowledgeSecret(fixture);
      expect(element(fixture).textContent).not.toContain(SECRET);
      expect(retainsSecret(fixture.componentInstance, SECRET)).toBeFalse();
    });

    it('does nothing when cancelled', async () => {
      const fixture = create(aConfiguredWebhook());
      await askToRotate(fixture);

      confirmButton(fixture, 'Cancel').click();
      await settle(fixture);

      http.expectNone((r) => r.url.endsWith('/rotate-secret'));
      expect(secretDialog(fixture).open).toBeFalse();
      expect(query<HTMLDialogElement>(fixture, 'app-confirm-dialog dialog').open).toBeFalse();
    });

    it('shows the error when rotation fails', async () => {
      const fixture = create(aConfiguredWebhook());
      await askToRotate(fixture);
      confirmButton(fixture, 'Rotate secret').click();
      await settle(fixture);

      request('POST', '/rotate-secret').flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('temporarily unavailable');
      expect(secretDialog(fixture).open).toBeFalse();
    });
  });

  describe('deleting the webhook', () => {
    async function askToDelete(fixture: ComponentFixture<WebhookSettingsPage>): Promise<void> {
      buttonByText(fixture, 'Delete webhook').click();
      await settle(fixture);
    }

    it('asks for confirmation with a danger button', async () => {
      const fixture = create(aConfiguredWebhook());

      await askToDelete(fixture);

      expect(confirmButton(fixture, 'Delete webhook').classList).toContain('btn-danger');
    });

    it('deletes the configuration and returns to the setup form', async () => {
      const fixture = create(aConfiguredWebhook());
      await askToDelete(fixture);

      confirmButton(fixture, 'Delete webhook').click();
      await settle(fixture);
      request('DELETE').flush(null, { status: 204, statusText: 'No Content' });
      fixture.detectChanges();

      expect(toasts.success).toHaveBeenCalledWith('Webhook deleted.');
      expect(inputByLabel(fixture, 'Endpoint URL').value).toBe('');
      expect(element(fixture).textContent).not.toContain('Send test event');
      expect(element(fixture).textContent).not.toContain('Delivery log');
    });

    it('keeps the webhook when the question is cancelled', async () => {
      const fixture = create(aConfiguredWebhook());
      await askToDelete(fixture);

      confirmButton(fixture, 'Cancel').click();
      await settle(fixture);

      http.expectNone((r) => r.method === 'DELETE');
      expect(element(fixture).textContent).toContain('Send test event');
    });

    it('shows the error and keeps the webhook when deleting fails', async () => {
      const fixture = create(aConfiguredWebhook());
      await askToDelete(fixture);
      confirmButton(fixture, 'Delete webhook').click();
      await settle(fixture);

      request('DELETE').flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('temporarily unavailable');
      expect(element(fixture).textContent).toContain('Send test event');
    });
  });
});
