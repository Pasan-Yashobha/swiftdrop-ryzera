import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { AuthSession } from '../models/auth.model';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let authApiSpy: jasmine.SpyObj<AuthApi>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockSession: AuthSession = {
    accessToken: 'valid-jwt-token',
    refreshToken: 'valid-refresh-token',
    expiresIn: 900,
    user: {
      id: 'e0a80101-7788-44aa-99bb-112233445566',
      email: 'customer@swiftdrop.lk',
      fullName: 'Kasun Silva',
      phone: '+94771234567',
      avatarUrl: null,
      role: 'CUSTOMER',
    },
  };

  beforeEach(() => {
    authApiSpy = jasmine.createSpyObj('AuthApi', ['login', 'signup', 'refresh', 'logout', 'isAuthUrl']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate'], { url: '/dashboard' });

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: AuthApi, useValue: authApiSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });

    service = TestBed.inject(AuthService);
  });

  it('starts unauthenticated with null token and user', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
  });

  it('sets user and token signals upon successful login', () => {
    authApiSpy.login.and.returnValue(of(mockSession));

    service.login({ email: 'customer@swiftdrop.lk', password: 'Password123!' }).subscribe((user) => {
      expect(user.email).toBe('customer@swiftdrop.lk');
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.isCustomer()).toBeTrue();
    expect(service.token()).toBe('valid-jwt-token');
    expect(service.user()?.fullName).toBe('Kasun Silva');
  });

  it('forces CUSTOMER role when registering via signup', () => {
    authApiSpy.signup.and.returnValue(of(mockSession));

    service.signup({
      fullName: 'Kasun Silva',
      email: 'customer@swiftdrop.lk',
      password: 'Password123!',
    }).subscribe();

    expect(authApiSpy.signup).toHaveBeenCalledWith({
      fullName: 'Kasun Silva',
      email: 'customer@swiftdrop.lk',
      password: 'Password123!',
      role: 'CUSTOMER',
    });
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('hydrates session from HttpOnly cookie on startup', () => {
    authApiSpy.refresh.and.returnValue(of(mockSession));

    service.hydrateFromCookie().subscribe((success) => {
      expect(success).toBeTrue();
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.initialized()).toBeTrue();
  });

  it('handles failed cookie hydration gracefully without crashing', () => {
    authApiSpy.refresh.and.returnValue(throwError(() => new Error('No active cookie session')));

    service.hydrateFromCookie().subscribe((success) => {
      expect(success).toBeFalse();
    });

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.initialized()).toBeTrue();
  });

  it('deduplicates concurrent refresh requests', () => {
    authApiSpy.refresh.and.returnValue(of(mockSession));

    service.refresh().subscribe();
    service.refresh().subscribe();

    expect(authApiSpy.refresh).toHaveBeenCalledTimes(1);
  });

  it('clears state and navigates to /login on logout', () => {
    authApiSpy.login.and.returnValue(of(mockSession));
    authApiSpy.logout.and.returnValue(of(undefined));

    service.login({ email: 'test@example.com', password: 'pwd' }).subscribe();
    expect(service.isAuthenticated()).toBeTrue();

    service.logout({ expired: true });

    expect(authApiSpy.logout).toHaveBeenCalled();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { reason: 'expired', returnUrl: '/dashboard' },
    });
  });
});
