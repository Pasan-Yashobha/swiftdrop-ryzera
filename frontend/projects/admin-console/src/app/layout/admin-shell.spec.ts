import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NotAuthorisedPage } from '../features/auth/not-authorised.page';
import { signIn } from '../core/testing/auth';
import { buttonByText, element } from '../core/testing/dom';
import { AdminShell } from './admin-shell';
import { ADMIN_NAV } from './admin-nav';

describe('AdminShell', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('lists every navigation item and the signed-in admin', () => {
    signIn(http);
    const fixture = TestBed.createComponent(AdminShell);
    fixture.detectChanges();

    const links = Array.from(element(fixture).querySelectorAll('nav[aria-label="Admin"] a'));
    const expected = ADMIN_NAV.flatMap((section) => section.items);
    expect(links.map((link) => link.textContent?.trim())).toEqual(expected.map((i) => i.label));
    expect(links.map((link) => link.getAttribute('href'))).toEqual(expected.map((i) => i.path));
    expect(element(fixture).textContent).toContain('Notification logs');
    expect(element(fixture).textContent).toContain('Webhook deliveries');
    expect(element(fixture).textContent).toContain('Kasun Silva');
  });

  it('signs out', () => {
    signIn(http);
    const fixture = TestBed.createComponent(AdminShell);
    fixture.detectChanges();

    buttonByText(fixture, 'Sign out').click();

    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], undefined);
  });
});

describe('NotAuthorisedPage', () => {
  it('tells a signed-in non-admin they are not authorised', () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    signIn(http, 'MERCHANT');

    const fixture = TestBed.createComponent(NotAuthorisedPage);
    fixture.detectChanges();

    const alert = element(fixture).querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Not authorised');
    expect(alert?.textContent).toContain('MERCHANT');
    http.verify();
    sessionStorage.clear();
  });
});
