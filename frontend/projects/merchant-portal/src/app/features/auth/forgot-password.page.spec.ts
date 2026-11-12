import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/config/app-config';
import { buttonByText, element, inputByLabel, query, type } from '../../core/testing/dom';
import { ForgotPasswordPage } from './forgot-password.page';

describe('ForgotPasswordPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ForgotPasswordPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renders email input and dispatch button', () => {
    expect(inputByLabel(fixture, 'EMAIL')).toBeTruthy();
    expect(buttonByText(fixture, 'DISPATCH RECOVERY LINK')).toBeTruthy();
  });

  it('validates email before calling the server', () => {
    buttonByText(fixture, 'DISPATCH RECOVERY LINK').click();
    fixture.detectChanges();

    http.expectNone('/api/v1/auth/forgot-password');
    expect(element(fixture).textContent).toContain('Email is required.');
  });

  it('dispatches recovery request and renders success confirmation state', () => {
    type(inputByLabel(fixture, 'EMAIL'), 'merchant@swiftdrop.com');
    buttonByText(fixture, 'DISPATCH RECOVERY LINK').click();
    fixture.detectChanges();

    const req = http.expectOne('/api/v1/auth/forgot-password');
    expect(req.request.body).toEqual({ email: 'merchant@swiftdrop.com' });
    req.flush({ message: 'If an account with that email exists, we have sent a password reset link.' });
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('If an account with that email exists');
    expect(element(fixture).textContent).toContain('Return to Log in');
  });

  it('updates live tracking hint when quick track is submitted', () => {
    const trackInput = query<HTMLInputElement>(fixture, '#quickTrackInputForgot');
    trackInput.value = 'SD-7777-9999';
    trackInput.dispatchEvent(new Event('input'));
    buttonByText(fixture, 'GO').click();
    fixture.detectChanges();

    expect(element(fixture).textContent).toContain('SD-7777-9999');
    expect(element(fixture).textContent).toContain('In Transit');
  });
});

