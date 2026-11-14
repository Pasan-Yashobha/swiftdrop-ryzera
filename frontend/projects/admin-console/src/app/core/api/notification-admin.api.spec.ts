import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { aNotification, aPage, anSms, aTemplate } from '../testing/test-data';
import { NotificationAdminApi } from './notification-admin.api';

const BASE = '/api/v1/admin/notifications';

describe('NotificationAdminApi', () => {
  let api: NotificationAdminApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(NotificationAdminApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the log with every filter as a query parameter', () => {
    let total = -1;
    api
      .list({
        status: 'FAILED',
        channel: 'SMS',
        eventType: 'parcel.status.delivered',
        from: '2026-11-01T00:00:00Z',
        to: '2026-11-14T23:59:59.999Z',
        page: 2,
        size: 20,
      })
      .subscribe((page) => (total = page.totalElements));

    const request = http.expectOne((r) => r.url === BASE);
    expect(request.request.method).toBe('GET');
    const params = request.request.params;
    expect(params.get('status')).toBe('FAILED');
    expect(params.get('channel')).toBe('SMS');
    expect(params.get('eventType')).toBe('parcel.status.delivered');
    expect(params.get('from')).toBe('2026-11-01T00:00:00Z');
    expect(params.get('to')).toBe('2026-11-14T23:59:59.999Z');
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('20');
    request.flush(aPage([aNotification()]));

    expect(total).toBe(1);
  });

  it('leaves empty filters out of the query string', () => {
    api.list({ status: null, channel: undefined, eventType: '  ', page: 0, size: 20 }).subscribe();

    const request = http.expectOne((r) => r.url === BASE);
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    request.flush(aPage([]));
  });

  it('resends a notification with POST and returns the updated row', () => {
    let status = '';
    api.resend('n-1').subscribe((log) => (status = log.status));

    const request = http.expectOne(`${BASE}/n-1/resend`);
    expect(request.request.method).toBe('POST');
    request.flush(aNotification({ status: 'SENT' }));

    expect(status).toBe('SENT');
  });

  it('pages through the mock SMS inbox', () => {
    let to = '';
    api.smsInbox(1, 10).subscribe((page) => (to = page.content[0].to));

    const request = http.expectOne((r) => r.url === `${BASE}/sms-inbox`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
    request.flush(aPage([anSms()]));

    expect(to).toBe('07X XXX 4567');
  });

  it('loads the templates', () => {
    let count = 0;
    api.templates().subscribe((list) => (count = list.length));

    const request = http.expectOne(`${BASE}/templates`);
    expect(request.request.method).toBe('GET');
    request.flush([aTemplate(), aTemplate({ id: 't-2', channel: 'SMS', subject: null })]);

    expect(count).toBe(2);
  });

  it('updates a template with PUT and only the editable fields', () => {
    let body = '';
    api
      .updateTemplate('t-1', { subject: 'New subject', body: 'Hi {{name}}', active: false })
      .subscribe((template) => (body = template.body));

    const request = http.expectOne(`${BASE}/templates/t-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      subject: 'New subject',
      body: 'Hi {{name}}',
      active: false,
    });
    request.flush(aTemplate({ body: 'Hi {{name}}' }));

    expect(body).toBe('Hi {{name}}');
  });

  it('encodes ids in the path', () => {
    api.resend('a/b').subscribe();

    const request = http.expectOne(`${BASE}/a%2Fb/resend`);
    expect(request.request.method).toBe('POST');
    request.flush(aNotification());
  });
});
