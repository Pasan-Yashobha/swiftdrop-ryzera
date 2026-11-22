import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { element, query } from '../../core/testing/dom';
import { aPage } from '../../core/testing/test-data';
import { NotificationsPage } from './notifications.page';

const BASE = '/api/v1/admin/notifications';

describe('NotificationsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts on the log tab and switches tabs with the keyboard', () => {
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url === BASE).flush(aPage([]));
    fixture.detectChanges();

    const logsTab = query<HTMLButtonElement>(fixture, '#tab-logs');
    expect(logsTab.getAttribute('aria-selected')).toBe('true');
    expect(query(fixture, '[role="tabpanel"]').getAttribute('aria-labelledby')).toBe('tab-logs');

    logsTab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    http.expectOne((r) => r.url === `${BASE}/sms-inbox`).flush(aPage([]));
    fixture.detectChanges();

    expect(query(fixture, '#tab-sms').getAttribute('aria-selected')).toBe('true');
    expect(element(fixture).textContent).toContain('Mock SMS inbox');
  });

  it('opens the tab named in the query parameter', () => {
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.componentRef.setInput('tab', 'templates');
    fixture.detectChanges();

    http.expectOne(`${BASE}/templates`).flush([]);
    expect(query(fixture, '#tab-templates').getAttribute('aria-selected')).toBe('true');
  });
});
