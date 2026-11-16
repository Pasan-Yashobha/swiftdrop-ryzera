import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { RateCard, Zone } from '../../core/models/pricing.model';
import { buttonByText, element, inputByLabel, type } from '../../core/testing/dom';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { RateCardsPage } from './rate-cards.page';

const ZONES = '/api/v1/admin/zones';
const CARDS = '/api/v1/admin/rate-cards';

const colombo: Zone = { id: 'z-1', code: 'COLOMBO', name: 'Colombo', active: true };
const kandy: Zone = { id: 'z-2', code: 'KANDY', name: 'Kandy', active: true };
const jaffna: Zone = { id: 'z-3', code: 'JAFFNA', name: 'Jaffna', active: false };

const card: RateCard = {
  id: 'r-1',
  originZoneId: 'z-1',
  destinationZoneId: 'z-2',
  baseFee: 300,
  perKgFee: 100,
  smallSurcharge: 0,
  mediumSurcharge: 50,
  largeSurcharge: 120,
  expressMultiplier: 1.25,
  validFrom: '2026-01-01',
  validTo: null,
  active: true,
};

function page<T>(items: T[]) {
  return { items, page: 0, size: 100, totalElements: items.length };
}

function choose(fixture: ComponentFixture<unknown>, label: string, value: string): void {
  const select = inputByLabel(fixture, label) as unknown as HTMLSelectElement;
  select.value = value;
  select.dispatchEvent(new Event('change'));
}

describe('RateCardsPage', () => {
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

  function flushLists(zones: Zone[], cards: RateCard[]): void {
    http.expectOne((r) => r.url === ZONES && r.method === 'GET').flush(page(zones));
    http.expectOne((r) => r.url === CARDS && r.method === 'GET').flush(page(cards));
  }

  function create(zones: Zone[], cards: RateCard[]): ComponentFixture<RateCardsPage> {
    const fixture = TestBed.createComponent(RateCardsPage);
    fixture.detectChanges();
    flushLists(zones, cards);
    fixture.detectChanges();
    return fixture;
  }

  it('shows each card as a route between zone names', () => {
    const fixture = create([colombo, kandy], [card]);
    const row = element(fixture).querySelector('tbody tr') as HTMLElement;

    expect(row.textContent).toContain('Colombo → Kandy');
    expect(row.textContent).toContain('×1.25');
    expect(row.textContent).toContain('open');
  });

  it('offers only active zones for a new card', () => {
    const fixture = create([colombo, kandy, jaffna], []);
    const options = Array.from(
      (inputByLabel(fixture, 'From zone') as unknown as HTMLSelectElement).options,
    ).map((option) => option.textContent?.trim());

    expect(options.some((text) => text?.includes('Jaffna'))).toBeFalse();
    expect(options.some((text) => text?.includes('Kandy'))).toBeTrue();
  });

  it('does not send a card without zones', () => {
    const fixture = create([colombo, kandy], []);
    buttonByText(fixture, 'Add rate card').click();
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    expect(element(fixture).textContent).toContain('From zone is required.');
  });

  it('rejects an end date before the start date', () => {
    const fixture = create([colombo, kandy], []);
    choose(fixture, 'From zone', 'z-1');
    choose(fixture, 'To zone', 'z-2');
    type(inputByLabel(fixture, 'Valid from'), '2026-12-01');
    type(inputByLabel(fixture, 'Valid until'), '2026-11-01');
    buttonByText(fixture, 'Add rate card').click();
    fixture.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    expect(element(fixture).textContent).toContain('The end date must be after the start date.');
  });

  it('adds a card, sending numbers and null for an empty express multiplier', () => {
    const fixture = create([colombo, kandy], []);
    choose(fixture, 'From zone', 'z-1');
    choose(fixture, 'To zone', 'z-2');
    type(inputByLabel(fixture, 'Base fee'), '250');
    type(inputByLabel(fixture, 'Per kg'), '80');
    type(inputByLabel(fixture, 'Valid from'), '2026-12-01');
    buttonByText(fixture, 'Add rate card').click();

    const post = http.expectOne((r) => r.url === CARDS && r.method === 'POST');
    expect(post.request.body).toEqual(
      jasmine.objectContaining({
        originZoneId: 'z-1',
        destinationZoneId: 'z-2',
        baseFee: 250,
        perKgFee: 80,
        expressMultiplier: null,
        validFrom: '2026-12-01',
        validTo: null,
      }),
    );
    post.flush({ ...card, id: 'r-2' });
    flushLists([colombo, kandy], []);

    expect(toasts.success).toHaveBeenCalledWith('Rate card saved.');
  });

  it('edits a card with PUT', () => {
    const fixture = create([colombo, kandy], [card]);
    buttonByText(fixture, 'Edit').click();
    fixture.detectChanges();
    buttonByText(fixture, 'Save changes').click();

    const put = http.expectOne((r) => r.url === `${CARDS}/r-1` && r.method === 'PUT');
    expect(put.request.body.expressMultiplier).toBe(1.25);
    put.flush(card);
    flushLists([colombo, kandy], [card]);
  });

  it('deactivates a card after confirmation and reports a failure', async () => {
    const fixture = create([colombo, kandy], [card]);
    buttonByText(fixture, 'Deactivate').click();
    fixture.detectChanges();

    const dialog = document.querySelector('dialog[open]') as HTMLDialogElement;
    expect(dialog.textContent).toContain('Colombo → Kandy');
    (dialog.querySelector('.btn-danger') as HTMLButtonElement).click();
    await fixture.whenStable();

    http
      .expectOne((r) => r.url === `${CARDS}/r-1` && r.method === 'DELETE')
      .flush(
        { status: 404, code: 'NOT_FOUND', message: 'Rate card not found.' },
        { status: 404, statusText: 'Not Found' },
      );
    expect(toasts.error).toHaveBeenCalledWith('Rate card not found.');
  });
});
