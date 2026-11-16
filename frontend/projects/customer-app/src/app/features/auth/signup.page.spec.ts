import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AuthUser } from '../../core/models/auth.model';
import { SignupPage } from './signup.page';

describe('SignupPage', () => {
  let component: SignupPage;
  let fixture: ComponentFixture<SignupPage>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockUser: AuthUser = {
    id: 'user-2',
    email: 'newcustomer@swiftdrop.lk',
    fullName: 'New Customer',
    role: 'CUSTOMER',
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['signup']);

    await TestBed.configureTestingModule({
      imports: [SignupPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.returnValue(Promise.resolve(true));

    fixture = TestBed.createComponent(SignupPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the signup page', () => {
    expect(component).toBeTruthy();
  });

  it('validates password matching before allowing submission', () => {
    component['fullName'].set('New Customer');
    component['email'].set('newcustomer@swiftdrop.lk');
    component['password'].set('Password123!');
    component['confirmPassword'].set('DifferentPassword!');

    expect(component['passwordsMatch']()).toBeFalse();
    expect(component['canSubmit']()).toBeFalse();

    component['confirmPassword'].set('Password123!');
    expect(component['passwordsMatch']()).toBeTrue();
    expect(component['canSubmit']()).toBeTrue();
  });

  it('submits registration and navigates to /dashboard on success', () => {
    authServiceSpy.signup.and.returnValue(of(mockUser));

    component['fullName'].set('New Customer');
    component['email'].set('newcustomer@swiftdrop.lk');
    component['phone'].set('+94771234567');
    component['password'].set('Password123!');
    component['confirmPassword'].set('Password123!');

    component['onSubmit'](new Event('submit'));

    expect(authServiceSpy.signup).toHaveBeenCalledWith({
      fullName: 'New Customer',
      email: 'newcustomer@swiftdrop.lk',
      phone: '+94771234567',
      password: 'Password123!',
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('displays conflict message when email is already registered (409)', () => {
    const errorResponse = new HttpErrorResponse({ status: 409, error: { message: 'Email taken' } });
    authServiceSpy.signup.and.returnValue(throwError(() => errorResponse));

    component['fullName'].set('New Customer');
    component['email'].set('existing@swiftdrop.lk');
    component['password'].set('Password123!');
    component['confirmPassword'].set('Password123!');

    component['onSubmit'](new Event('submit'));

    expect(component['errorMessage']()).toContain('An account with this email already exists');
    expect(component['loading']()).toBeFalse();
  });
});
