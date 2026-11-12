import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_BASE_URL } from '../../core/config/app-config';
import { buttonByText, element, inputByLabel, type } from '../../core/testing/dom';
import { ResetPasswordPage } from './reset-password.page';

describe('ResetPasswordPage', () => {
  let http: HttpTestingController;
  let router: Router;
  let fixture: ComponentFixture<ResetPasswordPage>;

  function create(token = 'valid-test-token-123') {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(ResetPasswordPage);
    if (token) {
      fixture.componentRef.setInput('token', token);
    }
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('renders password inputs and updates rules as user types', () => {
    const f = create();
    expect(inputByLabel(f, 'NEW PASSWORD')).toBeTruthy();
    expect(inputByLabel(f, 'CONFIRM NEW PASSWORD')).toBeTruthy();

    const rules = () => Array.from(element(f).querySelectorAll('.rules li'));
    expect(rules().every((rule) => !rule.classList.contains('rules__met'))).toBeTrue();

    type(inputByLabel(f, 'NEW PASSWORD'), 'ValidPass123!');
    f.detectChanges();

    expect(rules().every((rule) => rule.classList.contains('rules__met'))).toBeTrue();
  });

  it('shows error when token is missing', () => {
    const f = create('');
    buttonByText(f, 'UPDATE PASSWORD').click();
    f.detectChanges();

    expect(element(f).textContent).toContain('Password reset token is missing');
  });

  it('submits valid password reset and displays success banner', () => {
    const f = create('my-secret-token');
    type(inputByLabel(f, 'NEW PASSWORD'), 'SecretPassword123!');
    type(inputByLabel(f, 'CONFIRM NEW PASSWORD'), 'SecretPassword123!');
    buttonByText(f, 'UPDATE PASSWORD').click();
    f.detectChanges();

    const req = http.expectOne('/api/v1/auth/reset-password');
    expect(req.request.body).toEqual({
      token: 'my-secret-token',
      newPassword: 'SecretPassword123!',
    });
    req.flush({ message: 'Your password has been successfully reset.' });
    f.detectChanges();

    expect(element(f).textContent).toContain('Password reset successfully');
  });
});

