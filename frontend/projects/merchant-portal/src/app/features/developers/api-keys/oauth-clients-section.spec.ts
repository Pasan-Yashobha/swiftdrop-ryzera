import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OAuthClient, OAuthClientCreated } from '../../../core/models/credential.model';
import { buttonByText, element, inputByLabel, query, type } from '../../../core/testing/dom';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import {
  acknowledgeSecret,
  anOAuthClient,
  confirmButton,
  retainsSecret,
  secretDialog,
  settle,
} from '../testing/developer-spec-helpers';
import { OAuthClientsSection } from './oauth-clients-section';

const CLIENTS_URL = '/api/v1/merchants/me/oauth-clients';
const SECRET = 'Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyMDEyMzQ1';

describe('OAuthClientsSection', () => {
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

  const listRequest = () => http.expectOne((r) => r.method === 'GET' && r.url === CLIENTS_URL);
  const postRequest = (url = CLIENTS_URL) =>
    http.expectOne((r) => r.method === 'POST' && r.url === url);

  function create(
    clients: OAuthClient[] = [anOAuthClient()],
  ): ComponentFixture<OAuthClientsSection> {
    const fixture = TestBed.createComponent(OAuthClientsSection);
    fixture.detectChanges();
    listRequest().flush(clients);
    fixture.detectChanges();
    return fixture;
  }

  function created(overrides: Partial<OAuthClient> = {}): OAuthClientCreated {
    return { ...anOAuthClient({ id: 'client-2', ...overrides }), clientSecret: SECRET };
  }

  function submit(fixture: ComponentFixture<OAuthClientsSection>): void {
    query<HTMLFormElement>(fixture, 'form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function scopeBox(
    fixture: ComponentFixture<OAuthClientsSection>,
    scope: string,
  ): HTMLInputElement {
    const label = Array.from(element(fixture).querySelectorAll('.scopes label')).find((candidate) =>
      candidate.textContent?.includes(scope),
    );
    return label?.querySelector('input') as HTMLInputElement;
  }

  describe('listing', () => {
    it('lists each client with its id, scopes, environment and status', () => {
      const fixture = create([
        anOAuthClient({ lastUsedAt: new Date(Date.now() - 2 * 3_600_000).toISOString() }),
        anOAuthClient({
          id: 'client-2',
          label: 'Reporting',
          environment: 'LIVE',
          scopes: ['orders:read'],
          status: 'REVOKED',
        }),
      ]);

      const rows = element(fixture).querySelectorAll('tbody tr');
      expect(rows.length).toBe(2);
      expect(rows[0].querySelector('th')?.textContent).toBe('Fulfilment worker');
      expect(rows[0].querySelector('app-environment-badge')?.textContent).toBe('TEST');
      expect(rows[0].querySelector('[data-label="Client ID"] code')?.textContent).toBe(
        'mc_abcdefghij0123456789',
      );
      expect(rows[0].querySelector('[data-label="Scopes"]')?.textContent).toContain(
        'orders:write orders:read',
      );
      expect(rows[0].querySelector('[data-label="Last used"]')?.textContent).toContain(
        '2 hours ago',
      );
      expect(rows[1].querySelector('app-environment-badge')?.textContent).toBe('LIVE');
      expect(rows[1].querySelector('app-status-badge')?.textContent).toContain('Revoked');
      expect(rows[1].querySelectorAll('button').length).toBe(1); // only Copy client ID
    });

    it('copies the client id', () => {
      const writeText = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
      const fixture = create();

      buttonByText(fixture, 'Copy client ID').click();

      expect(writeText).toHaveBeenCalledWith('mc_abcdefghij0123456789');
    });

    it('invites the merchant to create the first client', () => {
      const fixture = create([]);

      expect(element(fixture).textContent).toContain('No OAuth clients yet');
    });

    it('counts the active clients against the limit of ten', () => {
      const fixture = create([anOAuthClient(), anOAuthClient({ id: 'c2', status: 'REVOKED' })]);

      expect(query(fixture, '[data-testid="client-limit"]').textContent).toContain(
        '1 of 10 active clients used',
      );
    });

    it('shows the load error and retries', () => {
      const fixture = TestBed.createComponent(OAuthClientsSection);
      fixture.detectChanges();
      listRequest().flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      buttonByText(fixture, 'Try again').click();
      listRequest().flush([anOAuthClient()]);
      fixture.detectChanges();

      expect(element(fixture).querySelectorAll('tbody tr').length).toBe(1);
    });

    it('explains that the business is not approved', () => {
      const fixture = TestBed.createComponent(OAuthClientsSection);
      fixture.detectChanges();
      listRequest().flush(
        { code: 'MERCHANT_NOT_APPROVED', message: 'Your merchant account is not approved.' },
        { status: 403, statusText: 'Forbidden' },
      );
      fixture.detectChanges();

      expect(element(fixture).querySelector('a[href="/status"]')).not.toBeNull();
    });
  });

  describe('creating a client', () => {
    it('requires a label', () => {
      const fixture = create();

      submit(fixture);

      expect(element(fixture).textContent).toContain('Give the client a label.');
    });

    it('starts with both scopes selected and sends the chosen ones', async () => {
      const fixture = create([]);
      expect(scopeBox(fixture, 'orders:write').checked).toBeTrue();
      expect(scopeBox(fixture, 'orders:read').checked).toBeTrue();

      scopeBox(fixture, 'orders:read').click();
      fixture.detectChanges();
      type(inputByLabel(fixture, 'Label'), 'Fulfilment worker');
      submit(fixture);

      const request = postRequest();
      expect(request.request.body).toEqual({
        label: 'Fulfilment worker',
        environment: 'TEST',
        scopes: ['orders:write'],
      });
      request.flush(created({ scopes: ['orders:write'] }));
      listRequest().flush([]);
      await settle(fixture);
    });

    it('refuses to create a client without any scope', () => {
      const fixture = create();
      scopeBox(fixture, 'orders:write').click();
      scopeBox(fixture, 'orders:read').click();
      fixture.detectChanges();
      type(inputByLabel(fixture, 'Label'), 'Worker');

      submit(fixture);

      expect(element(fixture).textContent).toContain('Select at least one scope.');
    });

    it('keeps the scopes in a fixed order however they were ticked', async () => {
      const fixture = create([]);
      scopeBox(fixture, 'orders:write').click();
      scopeBox(fixture, 'orders:write').click();
      fixture.detectChanges();
      type(inputByLabel(fixture, 'Label'), 'Worker');
      submit(fixture);

      const request = postRequest();
      expect(request.request.body.scopes).toEqual(['orders:write', 'orders:read']);
      request.flush(created());
      listRequest().flush([]);
      await settle(fixture);
    });

    it('explains the chosen environment', () => {
      const fixture = create();
      const select = query<HTMLSelectElement>(fixture, 'select');
      expect(element(fixture).textContent).toContain('create simulated orders only');

      select.value = 'LIVE';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('create real parcels');
    });

    it('shows the client secret once, with the client id', async () => {
      const fixture = create([]);
      type(inputByLabel(fixture, 'Label'), 'Fulfilment worker');
      submit(fixture);

      postRequest().flush(created());
      listRequest().flush([anOAuthClient()]);
      await settle(fixture);

      const dialog = secretDialog(fixture);
      expect(dialog.open).toBeTrue();
      expect(dialog.textContent).toContain('OAuth client created');
      expect(dialog.textContent).toContain(SECRET);
      expect(dialog.textContent).toContain('mc_abcdefghij0123456789');
      expect(dialog.textContent).toContain('client secret');
      expect(element(fixture).querySelector('table')?.textContent).not.toContain(SECRET);
    });

    it('drops the secret from the page and the component once the dialog is closed', async () => {
      const fixture = create([]);
      type(inputByLabel(fixture, 'Label'), 'Fulfilment worker');
      submit(fixture);
      postRequest().flush(created());
      listRequest().flush([]);
      await settle(fixture);

      await acknowledgeSecret(fixture);

      expect(secretDialog(fixture).open).toBeFalse();
      expect(element(fixture).textContent).not.toContain(SECRET);
      expect(retainsSecret(fixture.componentInstance, SECRET)).toBeFalse();
    });

    it('shows the limit message when the server refuses an eleventh client', () => {
      const fixture = create();
      type(inputByLabel(fixture, 'Label'), 'Worker');
      submit(fixture);

      postRequest().flush(
        { code: 'INVALID_STATE', message: 'You can have at most 10 active OAuth clients.' },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('at most 10 active OAuth clients');
      expect(inputByLabel(fixture, 'Label').value).toBe('Worker');
    });

    it('stops offering new clients once ten are active', () => {
      const clients = Array.from({ length: 10 }, (_, index) => anOAuthClient({ id: `c${index}` }));
      const fixture = create(clients);

      expect(query<HTMLButtonElement>(fixture, 'button[type="submit"]').disabled).toBeTrue();
      expect(element(fixture).textContent).toContain('reached the limit of 10 active clients');
    });
  });

  describe('rotating the secret', () => {
    const ROTATE_URL = `${CLIENTS_URL}/client-1/rotate-secret`;

    async function askToRotate(fixture: ComponentFixture<OAuthClientsSection>): Promise<void> {
      buttonByText(fixture, 'Rotate secret').click();
      await settle(fixture);
    }

    it('warns that the current secret stops working immediately', async () => {
      const fixture = create();

      await askToRotate(fixture);

      const dialog = query<HTMLDialogElement>(fixture, 'app-confirm-dialog dialog');
      expect(dialog.open).toBeTrue();
      expect(dialog.textContent).toContain('stops working immediately');
    });

    it('shows the new secret once after confirming', async () => {
      const fixture = create();
      await askToRotate(fixture);

      confirmButton(fixture, 'Rotate secret').click();
      await settle(fixture);
      postRequest(ROTATE_URL).flush({ clientSecret: SECRET });
      await settle(fixture);

      const dialog = secretDialog(fixture);
      expect(dialog.textContent).toContain('Client secret rotated');
      expect(dialog.textContent).toContain(SECRET);

      await acknowledgeSecret(fixture);
      expect(element(fixture).textContent).not.toContain(SECRET);
      expect(retainsSecret(fixture.componentInstance, SECRET)).toBeFalse();
    });

    it('does nothing when cancelled', async () => {
      const fixture = create();
      await askToRotate(fixture);

      confirmButton(fixture, 'Cancel').click();
      await settle(fixture);

      http.expectNone(ROTATE_URL);
      expect(query<HTMLDialogElement>(fixture, 'app-confirm-dialog dialog').open).toBeFalse();
      expect(secretDialog(fixture).open).toBeFalse();
    });

    it('shows the error when rotation fails', async () => {
      const fixture = create();
      await askToRotate(fixture);
      confirmButton(fixture, 'Rotate secret').click();
      await settle(fixture);

      postRequest(ROTATE_URL).flush(
        { code: 'NOT_FOUND', message: 'That OAuth client does not exist.' },
        { status: 404, statusText: 'Not Found' },
      );
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('That OAuth client does not exist.');
    });
  });

  describe('revoking a client', () => {
    const REVOKE_URL = `${CLIENTS_URL}/client-1`;

    async function askToRevoke(fixture: ComponentFixture<OAuthClientsSection>): Promise<void> {
      buttonByText(fixture, 'Revoke').click();
      await settle(fixture);
    }

    it('revokes the client once confirmed and reloads the list', async () => {
      const fixture = create();
      await askToRevoke(fixture);
      expect(confirmButton(fixture, 'Revoke client').classList).toContain('btn-danger');

      confirmButton(fixture, 'Revoke client').click();
      await settle(fixture);
      http
        .expectOne((r) => r.method === 'DELETE' && r.url === REVOKE_URL)
        .flush(null, { status: 204, statusText: 'No Content' });
      listRequest().flush([anOAuthClient({ status: 'REVOKED' })]);
      fixture.detectChanges();

      expect(toasts.success).toHaveBeenCalledWith('Client "Fulfilment worker" revoked.');
      expect(query(fixture, 'tbody tr').textContent).toContain('Revoked');
      expect(document.activeElement).toBe(query(fixture, '#oauth-clients-heading'));
    });

    it('keeps the client when the question is cancelled', async () => {
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
      confirmButton(fixture, 'Revoke client').click();
      await settle(fixture);

      http
        .expectOne((r) => r.method === 'DELETE')
        .flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(element(fixture).textContent).toContain('temporarily unavailable');
    });
  });
});
