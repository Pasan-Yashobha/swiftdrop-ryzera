import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApiKey, ApiKeyCreated } from '../../../core/models/credential.model';
import { buttonByText, element, inputByLabel, query, type } from '../../../core/testing/dom';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import {
  acknowledgeSecret,
  anApiKey,
  confirmButton,
  retainsSecret,
  secretDialog,
  settle,
} from '../testing/developer-spec-helpers';
import { ApiKeysSection } from './api-keys-section';

const KEYS_URL = '/api/v1/merchants/me/api-keys';
const SECRET = 'sd_live_ab12cd34_Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyMDE';
const HOUR_MS = 3_600_000;

describe('ApiKeysSection', () => {
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

  const listRequest = () => http.expectOne((r) => r.method === 'GET' && r.url === KEYS_URL);
  const postRequest = (url = KEYS_URL) =>
    http.expectOne((r) => r.method === 'POST' && r.url === url);

  function create(keys: ApiKey[] = [anApiKey()]): ComponentFixture<ApiKeysSection> {
    const fixture = TestBed.createComponent(ApiKeysSection);
    fixture.detectChanges();
    listRequest().flush(keys);
    fixture.detectChanges();
    return fixture;
  }

  function created(overrides: Partial<ApiKey> = {}, apiKey = SECRET): ApiKeyCreated {
    return { ...anApiKey({ id: 'key-2', ...overrides }), apiKey };
  }

  function submit(fixture: ComponentFixture<ApiKeysSection>): void {
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function createKey(fixture: ComponentFixture<ApiKeysSection>, label = 'Shop backend'): void {
    type(inputByLabel(fixture, 'Label'), label);
    submit(fixture);
  }

  function rotateDialogButton(fixture: ComponentFixture<ApiKeysSection>, label: string) {
    const match = Array.from(
      element(fixture).querySelectorAll<HTMLButtonElement>('app-rotate-key-dialog dialog button'),
    ).find((button) => button.textContent?.trim() === label);
    if (!match) {
      throw new Error(`The rotate dialog has no "${label}" button`);
    }
    return match;
  }

  describe('listing', () => {
    it('shows a loading placeholder until the keys arrive', () => {
      const fixture = TestBed.createComponent(ApiKeysSection);
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Loading API keys');
      listRequest().flush([]);
    });

    it('lists each key with its environment, prefix, status and dates', () => {
      const fixture = create([
        anApiKey({ lastUsedAt: new Date(Date.now() - 5 * 60_000).toISOString() }),
        anApiKey({
          id: 'key-2',
          label: 'Staging',
          environment: 'TEST',
          keyPrefix: 'sd_test_zz99yy88',
        }),
      ]);

      const rows = element(fixture).querySelectorAll('tbody tr');
      expect(rows.length).toBe(2);
      expect(rows[0].querySelector('th')?.textContent).toBe('Shop backend');
      expect(rows[0].querySelector('app-environment-badge')?.textContent).toBe('LIVE');
      expect(rows[0].querySelector('code')?.textContent).toBe('sd_live_ab12cd34');
      expect(rows[0].querySelector('app-status-badge')?.textContent).toContain('Active');
      expect(rows[0].querySelector('[data-label="Last used"]')?.textContent).toContain(
        '5 minutes ago',
      );
      expect(rows[0].querySelector('[data-label="Created"]')?.textContent).toContain('Oct 1, 2026');
      expect(rows[1].querySelector('app-environment-badge')?.textContent).toBe('TEST');
      expect(rows[1].querySelector('[data-label="Last used"]')?.textContent).toContain('Never');
    });

    it('shows how long an old key keeps working after a rotation', () => {
      const fixture = create([
        anApiKey({ expiresAt: new Date(Date.now() + 23.5 * HOUR_MS).toISOString() }),
      ]);

      expect(query(fixture, 'tbody tr').textContent).toContain('expires in 23h');
    });

    it('does not offer to rotate a key that is already being replaced, but still allows revoking it', () => {
      const fixture = create([
        anApiKey({ expiresAt: new Date(Date.now() + 5 * HOUR_MS).toISOString() }),
      ]);

      const buttons = Array.from(element(fixture).querySelectorAll('tbody button')).map((button) =>
        button.textContent?.trim(),
      );
      expect(buttons.some((text) => text?.startsWith('Rotate'))).toBeFalse();
      expect(buttons.some((text) => text?.startsWith('Revoke'))).toBeTrue();
    });

    it('shows a revoked key without actions', () => {
      const fixture = create([anApiKey({ status: 'REVOKED', revokedAt: '2026-10-05T08:00:00Z' })]);

      expect(query(fixture, 'tbody tr').textContent).toContain('Revoked');
      expect(element(fixture).querySelectorAll('tbody button').length).toBe(1); // only Copy prefix
    });

    it('invites the merchant to create the first key', () => {
      const fixture = create([]);

      expect(element(fixture).textContent).toContain('No API keys yet');
      expect(element(fixture).querySelector('table')).toBeNull();
    });

    it('counts the active keys against the limit of ten', () => {
      const fixture = create([anApiKey(), anApiKey({ id: 'key-2', status: 'REVOKED' })]);

      expect(query(fixture, '[data-testid="key-limit"]').textContent).toContain(
        '1 of 10 active keys used',
      );
    });

    it('copies the key prefix', () => {
      const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
      const fixture = create();

      buttonByText(fixture, 'Copy prefix').click();

      expect(writeText).toHaveBeenCalledWith('sd_live_ab12cd34');
    });

    it('shows the load error and retries', () => {
      const fixture = TestBed.createComponent(ApiKeysSection);
      fixture.detectChanges();
      listRequest().flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('temporarily unavailable');
      buttonByText(fixture, 'Try again').click();
      listRequest().flush([anApiKey()]);
      fixture.detectChanges();

      expect(element(fixture).textContent).not.toContain('temporarily unavailable');
      expect(element(fixture).querySelectorAll('tbody tr').length).toBe(1);
    });

    it('explains that credentials need an approved business', () => {
      const fixture = TestBed.createComponent(ApiKeysSection);
      fixture.detectChanges();
      listRequest().flush(
        { code: 'MERCHANT_NOT_APPROVED', message: 'Your merchant account is not approved.' },
        { status: 403, statusText: 'Forbidden' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Your merchant account is not approved.');
      expect(query<HTMLAnchorElement>(fixture, 'a[href="/status"]').textContent).toContain(
        'application status',
      );
    });
  });

  describe('creating a key', () => {
    it('requires a label and sends nothing while it is missing', () => {
      const fixture = create();

      submit(fixture);

      expect(element(fixture).textContent).toContain('Give the key a label.');
      expect(document.activeElement).toBe(inputByLabel(fixture, 'Label'));
    });

    it('rejects a label that is too long', () => {
      const fixture = create();

      createKey(fixture, 'x'.repeat(101));

      expect(element(fixture).textContent).toContain('Label must be at most 100 characters.');
    });

    it('defaults to the sandbox and explains the difference when LIVE is chosen', () => {
      const fixture = create();
      const select = query<HTMLSelectElement>(fixture, 'select');

      expect(select.value).toBe('TEST');
      expect(element(fixture).textContent).toContain('no real parcel is created');

      select.value = 'LIVE';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('orders create real parcels');
    });

    it('creates the key and shows it once in the secret dialog', async () => {
      const fixture = create([]);
      const select = query<HTMLSelectElement>(fixture, 'select');
      select.value = 'LIVE';
      select.dispatchEvent(new Event('change'));
      createKey(fixture, '  Shop backend  ');

      const request = postRequest();
      expect(request.request.body).toEqual({ label: 'Shop backend', environment: 'LIVE' });
      request.flush(created());
      listRequest().flush([anApiKey()]);
      await settle(fixture);

      const dialog = secretDialog(fixture);
      expect(dialog.open).toBeTrue();
      expect(dialog.textContent).toContain(SECRET);
      expect(dialog.textContent).toContain('API key created');
      expect(element(fixture).querySelector('table')?.textContent).not.toContain(SECRET);
      expect(inputByLabel(fixture, 'Label').value).toBe('');
    });

    it('drops the secret from the page and the component once the dialog is closed', async () => {
      const fixture = create([]);
      createKey(fixture);
      postRequest().flush(created());
      listRequest().flush([anApiKey()]);
      await settle(fixture);
      expect(element(fixture).textContent).toContain(SECRET);

      await acknowledgeSecret(fixture);

      expect(secretDialog(fixture).open).toBeFalse();
      expect(element(fixture).textContent).not.toContain(SECRET);
      expect(retainsSecret(fixture.componentInstance, SECRET)).toBeFalse();
    });

    it('shows the limit message when the server refuses an eleventh key', () => {
      const fixture = create();
      createKey(fixture);

      postRequest().flush(
        { code: 'INVALID_STATE', message: 'You can have at most 10 active API keys.' },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('You can have at most 10 active API keys.');
      expect(inputByLabel(fixture, 'Label').value).toBe('Shop backend');
      expect(query<HTMLButtonElement>(fixture, 'button[type="submit"]').disabled).toBeFalse();
    });

    it('explains that the business is not approved when the server says so', () => {
      const fixture = create();
      createKey(fixture);

      postRequest().flush(
        { code: 'MERCHANT_NOT_APPROVED', message: 'Your merchant account is suspended.' },
        { status: 403, statusText: 'Forbidden' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Your merchant account is suspended.');
      expect(element(fixture).querySelector('a[href="/status"]')).not.toBeNull();
    });

    it('stops offering new keys once ten are active', () => {
      const keys = Array.from({ length: 10 }, (_, index) => anApiKey({ id: `key-${index}` }));
      const fixture = create(keys);

      expect(query<HTMLButtonElement>(fixture, 'button[type="submit"]').disabled).toBeTrue();
      expect(element(fixture).textContent).toContain(
        'You have reached the limit of 10 active keys',
      );
    });

    it('disables the button while the request is running', () => {
      const fixture = create();
      createKey(fixture);

      expect(query<HTMLButtonElement>(fixture, 'button[type="submit"]').textContent).toContain(
        'Creating...',
      );
      expect(query<HTMLButtonElement>(fixture, 'button[type="submit"]').disabled).toBeTrue();
      postRequest().flush(created());
      listRequest().flush([]);
    });
  });

  describe('rotating a key', () => {
    const ROTATE_URL = `${KEYS_URL}/key-1/rotate`;

    async function openRotateDialog(fixture: ComponentFixture<ApiKeysSection>): Promise<void> {
      buttonByText(fixture, 'Rotate').click();
      await settle(fixture);
    }

    it('asks for the grace period, defaulting to 24 hours', async () => {
      const fixture = create();

      await openRotateDialog(fixture);

      const dialog = query<HTMLDialogElement>(fixture, 'app-rotate-key-dialog dialog');
      expect(dialog.open).toBeTrue();
      expect(dialog.querySelector('h2')?.textContent).toContain('Shop backend');
      expect(dialog.querySelector('input')?.value).toBe('24');
    });

    it('rotates with the chosen grace period and shows the new key once', async () => {
      const fixture = create();
      await openRotateDialog(fixture);
      type(query<HTMLInputElement>(fixture, 'app-rotate-key-dialog input'), '6');

      rotateDialogButton(fixture, 'Rotate key').click();
      await settle(fixture);
      const request = postRequest(ROTATE_URL);
      expect(request.request.body).toEqual({ graceHours: 6 });
      request.flush(created({ keyPrefix: 'sd_live_new11new1' }));
      listRequest().flush([anApiKey()]);
      await settle(fixture);

      const dialog = secretDialog(fixture);
      expect(dialog.textContent).toContain('API key rotated');
      expect(dialog.textContent).toContain(SECRET);
      expect(dialog.textContent).toContain('keeps working for 6 more hours');
    });

    it('says the old key is gone when the grace period is 0', async () => {
      const fixture = create();
      await openRotateDialog(fixture);
      type(query<HTMLInputElement>(fixture, 'app-rotate-key-dialog input'), '0');

      rotateDialogButton(fixture, 'Rotate key').click();
      await settle(fixture);
      const request = postRequest(ROTATE_URL);
      expect(request.request.body).toEqual({ graceHours: 0 });
      request.flush(created());
      listRequest().flush([]);
      await settle(fixture);

      expect(secretDialog(fixture).textContent).toContain('revoked immediately');
    });

    it('does not keep the new key anywhere once the dialog is closed', async () => {
      const fixture = create();
      await openRotateDialog(fixture);
      rotateDialogButton(fixture, 'Rotate key').click();
      await settle(fixture);
      postRequest(ROTATE_URL).flush(created());
      listRequest().flush([]);
      await settle(fixture);

      await acknowledgeSecret(fixture);

      expect(element(fixture).textContent).not.toContain(SECRET);
      expect(retainsSecret(fixture.componentInstance, SECRET)).toBeFalse();
    });

    it('does nothing when cancelled', async () => {
      const fixture = create();
      await openRotateDialog(fixture);

      rotateDialogButton(fixture, 'Cancel').click();
      await settle(fixture);

      http.expectNone(ROTATE_URL);
      expect(query<HTMLDialogElement>(fixture, 'app-rotate-key-dialog dialog').open).toBeFalse();
      expect(secretDialog(fixture).open).toBeFalse();
    });

    it('shows the server error and keeps the list when rotation fails', async () => {
      const fixture = create();
      await openRotateDialog(fixture);
      rotateDialogButton(fixture, 'Rotate key').click();
      await settle(fixture);

      postRequest(ROTATE_URL).flush(
        { code: 'INVALID_STATE', message: 'Only active keys can be rotated.' },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('Only active keys can be rotated.');
      expect(element(fixture).querySelectorAll('tbody tr').length).toBe(1);
    });
  });

  describe('revoking a key', () => {
    const REVOKE_URL = `${KEYS_URL}/key-1`;

    async function askToRevoke(fixture: ComponentFixture<ApiKeysSection>): Promise<void> {
      buttonByText(fixture, 'Revoke').click();
      await settle(fixture);
    }

    it('asks for confirmation with a danger button', async () => {
      const fixture = create();

      await askToRevoke(fixture);

      const dialog = query<HTMLDialogElement>(fixture, 'app-confirm-dialog dialog');
      expect(dialog.open).toBeTrue();
      expect(dialog.querySelector('h2')?.textContent).toContain('Revoke "Shop backend"?');
      expect(confirmButton(fixture, 'Revoke key').classList).toContain('btn-danger');
    });

    it('revokes the key once confirmed and reloads the list', async () => {
      const fixture = create();
      await askToRevoke(fixture);

      confirmButton(fixture, 'Revoke key').click();
      await settle(fixture);
      const request = http.expectOne((r) => r.method === 'DELETE' && r.url === REVOKE_URL);
      request.flush(null, { status: 204, statusText: 'No Content' });
      listRequest().flush([anApiKey({ status: 'REVOKED' })]);
      fixture.detectChanges();

      expect(toasts.success).toHaveBeenCalledWith('Key "Shop backend" revoked.');
      expect(query(fixture, 'tbody tr').textContent).toContain('Revoked');
    });

    it('keeps the key when the question is cancelled', async () => {
      const fixture = create();
      await askToRevoke(fixture);

      confirmButton(fixture, 'Cancel').click();
      await settle(fixture);

      http.expectNone((r) => r.method === 'DELETE');
      expect(toasts.success).not.toHaveBeenCalled();
      expect(query(fixture, 'tbody tr').textContent).toContain('Active');
    });

    it('shows the error when revoking fails', async () => {
      const fixture = create();
      await askToRevoke(fixture);
      confirmButton(fixture, 'Revoke key').click();
      await settle(fixture);

      http
        .expectOne((r) => r.method === 'DELETE')
        .flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('temporarily unavailable');
      expect(toasts.success).not.toHaveBeenCalled();
    });
  });
});
