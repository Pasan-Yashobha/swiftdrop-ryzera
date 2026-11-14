import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { buttonByText, element, query } from '../../core/testing/dom';
import { aPage, anSms, aTemplate } from '../../core/testing/test-data';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { SmsInbox } from './sms-inbox';
import { TemplatesEditor } from './templates-editor';

const BASE = '/api/v1/admin/notifications';

describe('Notification sub-pages', () => {
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
  });

  afterEach(() => http.verify());

  describe('SmsInbox', () => {
    it('lists the mock SMS messages as plain text', () => {
      const fixture = TestBed.createComponent(SmsInbox);
      fixture.detectChanges();
      const request = http.expectOne((r) => r.url === `${BASE}/sms-inbox`);
      expect(request.request.params.get('page')).toBe('0');
      request.flush(
        aPage([anSms(), anSms({ id: 'sms-2', message: '<b>not bold</b>' })]),
      );
      fixture.detectChanges();

      const items = element(fixture).querySelectorAll('.sms');
      expect(items.length).toBe(2);
      expect(items[0].textContent).toContain('To 07X XXX 4567');
      expect(items[1].querySelector('.sms__message')?.textContent).toBe('<b>not bold</b>');
      expect(items[1].querySelector('b')).toBeNull();
    });
  });

  describe('TemplatesEditor', () => {
    it('edits a template in a dialog and saves it with PUT', () => {
      const fixture = TestBed.createComponent(TemplatesEditor);
      fixture.detectChanges();
      http
        .expectOne(`${BASE}/templates`)
        .flush([aTemplate(), aTemplate({ id: 't-2', channel: 'SMS', subject: null })]);
      fixture.detectChanges();

      expect(element(fixture).querySelectorAll('tbody tr').length).toBe(2);
      buttonByText(fixture, 'Edit').click();
      fixture.detectChanges();

      const dialog = query<HTMLDialogElement>(fixture, 'dialog');
      expect(dialog.open).toBeTrue();
      const body = query<HTMLTextAreaElement>(fixture, '#template-body');
      expect(body.value).toBe(aTemplate().body);
      expect(dialog.textContent).toContain('customerName, trackingNumber');

      body.value = 'Hello {{customerName}}';
      body.dispatchEvent(new Event('input'));
      const subject = query<HTMLInputElement>(fixture, '#template-subject');
      subject.value = 'Out for delivery';
      subject.dispatchEvent(new Event('input'));
      buttonByText(fixture, 'Save template').click();

      const request = http.expectOne(`${BASE}/templates/t-1`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({
        subject: 'Out for delivery',
        body: 'Hello {{customerName}}',
        active: true,
      });
      request.flush(aTemplate({ subject: 'Out for delivery', body: 'Hello {{customerName}}' }));
      fixture.detectChanges();

      expect(dialog.open).toBeFalse();
      expect(toasts.success).toHaveBeenCalled();
      expect(element(fixture).querySelector('tbody tr')?.textContent).toContain(
        'Out for delivery',
      );
    });

    it('sends a null subject for an SMS template without one', () => {
      const fixture = TestBed.createComponent(TemplatesEditor);
      fixture.detectChanges();
      http.expectOne(`${BASE}/templates`).flush([aTemplate({ channel: 'SMS', subject: null })]);
      fixture.detectChanges();

      buttonByText(fixture, 'Edit').click();
      fixture.detectChanges();
      expect(element(fixture).querySelector('#template-subject')).toBeNull();
      buttonByText(fixture, 'Save template').click();

      const request = http.expectOne(`${BASE}/templates/t-1`);
      expect(request.request.body.subject).toBeNull();
      request.flush(aTemplate({ channel: 'SMS', subject: null }));
    });
  });
});
