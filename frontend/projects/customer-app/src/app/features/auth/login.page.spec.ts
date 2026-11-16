import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AuthUser } from '../../core/models/auth.model';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let component: LoginPage;
  let fixture: ComponentFixture<LoginPage>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockUser: AuthUser = {
    id: 'user-1',
    email: 'test@swiftdrop.lk',
    fullName: 'Test User',
    role: 'CUSTOMER',
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap({ returnUrl: '/history' })),
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.returnValue(Promise.resolve(true));

    fixture = TestBed.createComponent(LoginPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the login page', () => {
    expect(component).toBeTruthy();
  });

  it('submits valid credentials and navigates to returnUrl on success', () => {
    authServiceSpy.login.and.returnValue(of(mockUser));

    component['email'].set('test@swiftdrop.lk');
    component['password'].set('Password123!');

    component['onSubmit'](new Event('submit'));

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      email: 'test@swiftdrop.lk',
      password: 'Password123!',
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/history');
  });

  it('displays error message when login fails with 401', () => {
    const errorResponse = new HttpErrorResponse({ status: 401, error: { message: 'Invalid credentials' } });
    authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

    component['email'].set('wrong@swiftdrop.lk');
    component['password'].set('Password123!');

    component['onSubmit'](new Event('submit'));

    expect(component['errorMessage']()).toBe('The email or password you entered is incorrect.');
    expect(component['loading']()).toBeFalse();
  });
});
