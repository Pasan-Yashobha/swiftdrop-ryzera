import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { MerchantStore } from './core/merchant/merchant.store';
import { buttonByText, element, query } from './core/testing/dom';
import { aMerchant, aSession } from './core/testing/test-data';

@Component({ template: '<input id="page-input" />' })
class BlankPage {}

describe('App shell', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<App>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'first', component: BlankPage },
          { path: 'second', component: BlankPage },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function signInWith(status: 'APPROVED' | 'PENDING' | null): void {
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    TestBed.inject(AuthService).login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession());
    if (status) {
      TestBed.inject(MerchantStore).refresh().subscribe();
      http.expectOne('/api/v1/merchants/me').flush(aMerchant({ status }));
    }
    fixture.detectChanges();
  }

  const links = () =>
    Array.from(element(fixture).querySelectorAll('nav a')).map((a) => a.textContent?.trim());

  it('provides a skip link, a focusable main landmark, a footer and the notification region', () => {
    expect(query(fixture, 'a.skip-link').textContent).toContain('Skip to main content');
    expect(query(fixture, 'main#main').getAttribute('tabindex')).toBe('-1');
    expect(query(fixture, 'footer').textContent).toContain('SwiftDrop Merchant Portal');
    expect(query(fixture, '[aria-live="polite"]')).toBeTruthy();
  });

  it('moves focus to the main area from the skip link without navigating', () => {
    const event = new MouseEvent('click', { cancelable: true, bubbles: true });

    query(fixture, 'a.skip-link').dispatchEvent(event);

    expect(event.defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(query(fixture, 'main'));
  });

  describe('focus after navigation', () => {
    let router: Router;

    beforeEach(async () => {
      router = TestBed.inject(Router);
      await router.navigateByUrl('/first');
    });

    it('stays where it is on the first navigation', () => {
      expect(document.activeElement).not.toBe(query(fixture, 'main'));
    });

    it('moves to the main area when the page changes', async () => {
      await router.navigateByUrl('/second');

      expect(document.activeElement).toBe(query(fixture, 'main'));
    });

    it('keeps focus when only the query string or fragment changes', async () => {
      fixture.detectChanges();
      const input = query<HTMLInputElement>(fixture, '#page-input');
      input.focus();

      await router.navigateByUrl('/first?search=ab');
      await router.navigateByUrl('/first?search=ab#section');

      expect(document.activeElement).toBe(input);
    });
  });

  it('offers sign in and sign up to visitors', () => {
    expect(links()).toEqual(['Sign in', 'Create account']);
    expect(element(fixture).querySelector('.header__chip')).toBeNull();
  });

  it('shows the full navigation and the role / status chip to an approved merchant', () => {
    signInWith('APPROVED');

    expect(links()).toEqual(['Dashboard', 'Orders', 'API keys', 'Webhooks', 'API docs', 'Profile']);
    expect(query(fixture, '.header__chip').textContent).toContain('Merchant');
    expect(query(fixture, '.header__chip').textContent).toContain('Approved');
  });

  it('limits a pending merchant to the status page and profile', () => {
    signInWith('PENDING');

    expect(links()).toEqual(['Application status', 'Profile']);
    expect(query(fixture, '.header__chip').textContent).toContain('Pending');
  });

  it('points a merchant without a business to onboarding', () => {
    signInWith(null);

    expect(links()).toEqual(['Register your business']);
  });

  it('collapses the navigation behind a menu button that reports its state', () => {
    const toggle = query(fixture, '.header__toggle');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(query(fixture, '#primary-nav').classList).toContain('header__nav--open');
  });

  it('signs the user out', () => {
    signInWith('APPROVED');

    buttonByText(fixture, 'Sign out').click();
    fixture.detectChanges();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    expect(links()).toEqual(['Sign in', 'Create account']);
  });
});
