import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { Zone } from '../../core/models/pricing.model';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { ZonesPage } from './zones.page';

const URL = '/api/v1/admin/zones';

const colombo: Zone = { id: 'z-1', code: 'COLOMBO', name: 'Colombo', active: true };
const galle: Zone = {
  id: 'z-2',
  code: 'GALLE',
  name: 'Galle',
  description: 'Southern coast',
  active: false,
};

describe('ZonesPage', () => {
  let http: HttpTestingController;
  let toasts: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    spyOn(toasts, 'success');
    spyOn(toasts, 'error');
  });

  afterEach(() => {
    document.querySelectorAll('dialog[open]').forEach((d) => (d as HTMLDialogElement).close());
    http.verify();
  });

  const list = () => http.expectOne((r) => r.url === URL && r.method === 'GET');

  function create(zones: Zone[]): ComponentFixture<ZonesPage> {
    const fixture = TestBed.createComponent(ZonesPage);
    fixture.detectChanges();
    list().flush({ items: zones, page: 0, size: 100, totalElements: zones.length });
    fixture.detectChanges();
    return fixture;
  }

  const rows = (fixture: ComponentFixture<ZonesPage>) =>
    Array.from(element(fixture).querySelectorAll('tbody tr'));

  it('lists the zones and offers Deactivate only for active ones', () => {
    const fixture = create([colombo, galle]);

    const [first, second] = rows(fixture);
    expect(first.textContent).toContain('COLOMBO');
    expect(first.textContent).toContain('Deactivate');
    expect(second.textContent).toContain('Southern coast');
    expect(second.textContent).not.toContain('Deactivate');
    expect(second.classList).toContain('inactive-row');
  });

  it('shows an empty state when there are no zones', () => {
    const fixture = create([]);
    expect(element(fixture).textContent).toContain('No zones yet');
  });

  it('shows a load failure with a retry', () => {
    const fixture = TestBed.createComponent(ZonesPage);
    fixture.detectChanges();
    list().flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    buttonByText(fixture, 'Try again').click();
    list().flush({ items: [colombo], page: 0, size: 100, totalElements: 1 });
    fixture.detectChanges();

    expect(rows(fixture).length).toBe(1);
  });

  it('does not send an invalid zone code', () => {
    const fixture = create([]);
    type(inputByLabel(fixture, 'Zone code'), 'colombo');
    type(inputByLabel(fixture, 'Name'), 'Colombo');
    buttonByText(fixture, 'Add zone').click();
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    expect(element(fixture).textContent).toContain('Start with a capital letter');
  });

  it('adds a zone, trimming the text, then reloads the list', () => {
    const fixture = create([]);
    type(inputByLabel(fixture, 'Zone code'), 'KANDY');
    type(inputByLabel(fixture, 'Name'), '  Kandy  ');
    buttonByText(fixture, 'Add zone').click();

    const post = http.expectOne((r) => r.url === URL && r.method === 'POST');
    expect(post.request.body).toEqual({ code: 'KANDY', name: 'Kandy', description: '' });
    post.flush({ id: 'z-3', code: 'KANDY', name: 'Kandy', active: true });
    list().flush({ items: [], page: 0, size: 100, totalElements: 0 });

    expect(toasts.success).toHaveBeenCalledWith('Zone KANDY saved.');
  });

  it('edits a zone with PUT and shows a server error', () => {
    const fixture = create([colombo]);
    buttonByText(fixture, 'Edit').click();
    fixture.detectChanges();
    expect(inputByLabel(fixture, 'Name').value).toBe('Colombo');

    buttonByText(fixture, 'Save changes').click();
    http
      .expectOne((r) => r.url === `${URL}/z-1` && r.method === 'PUT')
      .flush(
        { status: 409, code: 'DUPLICATE_CODE', message: 'Zone code COLOMBO already exists.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(query(fixture, '[role="alert"]').textContent).toContain('already exists');
  });

  it('deactivates a zone after confirmation', async () => {
    const fixture = create([colombo]);
    buttonByText(fixture, 'Deactivate').click();
    fixture.detectChanges();

    const dialog = document.querySelector('dialog[open]') as HTMLDialogElement;
    expect(dialog.textContent).toContain('Deactivate COLOMBO?');
    (dialog.querySelector('.btn-danger') as HTMLButtonElement).click();
    await fixture.whenStable();

    http.expectOne((r) => r.url === `${URL}/z-1` && r.method === 'DELETE').flush(null);
    list().flush({ items: [], page: 0, size: 100, totalElements: 0 });
    expect(toasts.success).toHaveBeenCalledWith('Zone COLOMBO deactivated.');
  });

  it('keeps the zone when the confirmation is cancelled', async () => {
    const fixture = create([colombo]);
    buttonByText(fixture, 'Deactivate').click();
    fixture.detectChanges();

    (document.querySelector('dialog[open] .btn-ghost') as HTMLButtonElement).click();
    await fixture.whenStable();

    http.expectNone((r) => r.method === 'DELETE');
    expect(document.querySelector('dialog[open]')).toBeNull();
  });
});
