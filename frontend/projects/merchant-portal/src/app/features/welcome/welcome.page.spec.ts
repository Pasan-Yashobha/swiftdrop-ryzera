import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { aSession } from '../../core/testing/test-data';
import { WelcomePage } from './welcome.page';

describe('WelcomePage', () => {
  let fixture: ComponentFixture<WelcomePage>;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [WelcomePage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  function createWithUser(role: 'CUSTOMER' | 'COURIER' | 'ADMIN' | 'MERCHANT') {
    (auth as unknown as { session: { set(v: unknown): void } }).session.set(
      aSession({
        user: {
          id: 'user-123',
          email: `${role.toLowerCase()}@swiftdrop.example`,
          fullName: `Test ${role}`,
          role,
        },
      }),
    );
    fixture = TestBed.createComponent(WelcomePage);
    fixture.detectChanges();
    return fixture;
  }

  it('renders welcome greeting and role badge for a Customer', () => {
    const f = createWithUser('CUSTOMER');
    const el: HTMLElement = f.nativeElement;

    expect(el.textContent).toContain('Welcome, Test CUSTOMER');
    expect(el.textContent).toContain('CUSTOMER');
    expect(el.textContent).toContain('Package Tracking Simulator');
  });

  it('allows customer to track a package', () => {
    const f = createWithUser('CUSTOMER');
    const component = f.componentInstance;

    component.trackPackage('SWD-TEST-99');
    f.detectChanges();

    expect(f.nativeElement.textContent).toContain('SWD-TEST-99');
    expect(f.nativeElement.textContent).toContain('In Transit');
  });

  it('renders courier dispatch hub and toggles duty for a Courier', () => {
    const f = createWithUser('COURIER');
    const el: HTMLElement = f.nativeElement;
    const component = f.componentInstance;

    expect(el.textContent).toContain('Courier Dispatch & Route Hub');
    expect(el.textContent).toContain('On Duty');

    component.toggleCourierDuty();
    f.detectChanges();

    expect(el.textContent).toContain('On Break');
  });

  it('renders admin telemetry for an Admin', () => {
    const f = createWithUser('ADMIN');
    const el: HTMLElement = f.nativeElement;

    expect(el.textContent).toContain('Platform Administration');
    expect(el.textContent).toContain('RS256 (2048-bit)');
    expect(el.textContent).toContain('Superuser');
  });

  it('signs out when sign out button is clicked', () => {
    const f = createWithUser('CUSTOMER');
    const spy = spyOn(auth, 'logout');

    f.componentInstance.signOut();

    expect(spy).toHaveBeenCalled();
  });
});