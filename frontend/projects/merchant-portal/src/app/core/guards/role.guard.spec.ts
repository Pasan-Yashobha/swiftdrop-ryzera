import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { UserRole } from '../models/auth.model';
import { aSession } from '../testing/test-data';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function signIn(role: UserRole): void {
    TestBed.inject(AuthService).login({ email: 'a@b.lk', password: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush(aSession({ user: { ...aSession().user, role } }));
  }

  function run(roles: UserRole[], url = '/receipts'): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      roleGuard(...roles)({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  function target(result: boolean | UrlTree): string {
    expect(result instanceof UrlTree).toBeTrue();
    return router.serializeUrl(result as UrlTree);
  }

  it('lets an allowed role through', () => {
    signIn('CUSTOMER');
    expect(run(['CUSTOMER', 'ADMIN'])).toBeTrue();
  });

  it('sends anonymous visitors to the login page, remembering the page', () => {
    expect(target(run(['CUSTOMER']))).toBe('/login?returnUrl=%2Freceipts');
  });

  it('sends a merchant without access to the dashboard', () => {
    signIn('MERCHANT');
    expect(target(run(['CUSTOMER', 'ADMIN']))).toBe('/dashboard');
  });

  it('sends other roles without access to the welcome hub', () => {
    signIn('COURIER');
    expect(target(run(['ADMIN']))).toBe('/welcome');
  });
});
